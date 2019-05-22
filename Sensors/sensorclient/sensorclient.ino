
#include "cactus_io_AM2302.h"

//////////////////////////////////////////////////////////////////////////////////////////////////
// Classes

class Sensor {
protected: 
  unsigned long _cycleStartTime; // when we started on the period/cycle of the sensor
  virtual double getRawValue() = 0; // get raw measurement directly from sensor at the current time
public:
  String sensorName;
  unsigned long cycleTime; // time it takes to finish one period
  Sensor() {}
  virtual void calibrate() = 0; // perform one-time calibration of sensor, likely at startup time
  virtual String getValue() = 0; // get some sort of aggregated or corrected value

  // State machine functions
  bool canGetNewValue;
  virtual void manageState(unsigned long currentTime) = 0;
};

class SensorMQ : public Sensor {
protected:
  int _pinData; // the analog pin of the arduino where we receive result voltage (analog in), the higher voltage, the higher concentration of gas
  int _pinDigitalSwitch; // the digital pin that controls a relay switching between two input voltages to the sensor
  int _pinDigital; // the digital pin, which is either 0 or 1, depending on if the gas measurement is over a certain amount (can be configured with the load resistor)
  bool _isHeating = false;

  double _analogMeasurementFresh;
  double _rRatioFresh; // r-ratio constant in fresh air as defined by datasheet
  double _r0; // a constant that is determined based on sensor results in clean air
  double _funcConstantA; // a constant that is determined by finding the regression of the curve describing sensor output in the datasheet 
  double _funcConstantB;  // another constant from the same regression
  double _rl; // load resistance in Ohm
  double _vc = 5; // control voltage in Volt

  // variables for humidity correction, gained by using polynomial regression on the two functions in the datasheet
  // "pink" and "blue" are the colors of those two functions
  double _aPink; 
  double _bPink;
  double _cPink;
  double _aBlue;
  double _bBlue;
  double _cBlue;
  double _rhPink = 85;
  double _rhBlue = 33;
  double _temp = 20; // temperature in degrees celcius
  double _rh = 33; // relative humidity percentage

  int _numMeasurements = 0;
  double _aggregatedValue = 0;
public:
  SensorMQ(int pinData, int pinDigital, int pinDigitalSwitch) { 
    _pinData = pinData; 
    _pinDigital = pinDigital; 
    _pinDigitalSwitch = pinDigitalSwitch;
    pinMode(_pinData, INPUT); 
    pinMode(_pinDigital, INPUT);
    pinMode(_pinDigitalSwitch, OUTPUT);

    canGetNewValue = false;
    _cycleStartTime = 0;
  }
  void calibrate() { /* no need to calibrate anything in particular */ }
  void setTemperatureHumidity(double temp, double rh) {
    _temp = temp;
    _rh = rh;
  }
  void performAggregation(double newValue) {
    _aggregatedValue += newValue;
    _numMeasurements++;
  }
  double convertVRL(double analogValue) {
    // analogValue is returned by the reading the sensor with analogRead and is a number between 0 and 1023
    return (analogValue / 1023) * 5; 
    // voltage over the load resistor in Volt: basically, the "output" voltage of the circuit, 
    // multiplied with 5, as it is the maximum voltage that can be returned by the arduino
  }
  double convertRS(double vrl) {
    return ((_vc * _rl) / vrl) - _rl; // rs is resistance over the sensor in Ohm
  }
  double convertCorrectedRRatio(double rRatio, double temp, double rh) {
    double rrPink = _aPink * pow(temp, 2) + _bPink * temp + _cPink;
    double rrBlue = _aBlue * pow(temp, 2) + _bBlue * temp + _cBlue;
    double rrDiff = rrBlue - rrPink;
    double rhDiff = _rhBlue - _rhPink;
    double constPerRH = rrDiff / rhDiff;
    double htFactor = constPerRH * (rh - _rhPink) + rrPink;
    return rRatio * htFactor;
  }
  double convertPPM(double analogValue) {
    double vrl = convertVRL(analogValue);
    double rs = convertRS(vrl);
    double rRatio = rs / _r0;
    double cRRatio = convertCorrectedRRatio(rRatio, _temp, _rh);
    return pow((cRRatio / _funcConstantA), (1 / _funcConstantB)); 
    // A power function of the form PPM(RRatio) = (a / RRatio) ^ (1 / b)
  }
  double getRawValue() {
    return analogRead(_pinData);
  }
  double getDigitalValue() {
    return digitalRead(_pinDigital);
  }
  String getValue() {
    int nums = _numMeasurements;
    double aggs = _aggregatedValue;
    _numMeasurements = 0;
    _aggregatedValue = 0;
    canGetNewValue = false;
    return String(aggs / nums, 4); // return average
  }
  void cool() { // turn off heating pin
    digitalWrite(_pinDigitalSwitch, LOW); 
    _isHeating = false;
  }
  void heat() {
    digitalWrite(_pinDigitalSwitch, HIGH); 
    _isHeating = true;
  }
  void startNewCycle(unsigned long newCycleStartTime) {
    _cycleStartTime = newCycleStartTime;
  }
};

class SensorMQ7 : public SensorMQ {
protected:
  unsigned long _heatPeriod = 60000;
  unsigned long _coolPeriod = 90000; 
public:
  SensorMQ7(int pinData, int pinDigital, int pinDigitalSwitch) : SensorMQ(pinData, pinDigital, pinDigitalSwitch) { 
    sensorName = String("MQ7");
    cycleTime = _heatPeriod + _coolPeriod;
    _analogMeasurementFresh = 45.31378;
    _rRatioFresh = 1;
    _funcConstantA = 0.8494;
    _funcConstantB = -0.5455;
    _rl = 10000;
    _r0 = (convertRS(convertVRL(_analogMeasurementFresh))) / _rRatioFresh;
    _aPink = 0.000102;
    _bPink = -0.011;
    _cPink = 1.044;
    _aBlue = 0.0002475;
    _bBlue = -0.017;
    _cBlue = 1.23;
  }
  void manageState(unsigned long currentTime) {
    if (_cycleStartTime == 0) { // if we have not yet started a cycle
      startNewCycle(currentTime); // sets cycle time and starts the cycle by heating 
      heat();
    }
    unsigned long cycleProgress = currentTime - _cycleStartTime;

    if (cycleProgress >= cycleTime) { // if we're done with a measurement cycle
      canGetNewValue = true;
      startNewCycle(currentTime);
      heat();
    }
    else if (cycleProgress >= _heatPeriod && _isHeating) { // switch state to cooling
      cool();
    }
    else if (cycleProgress >= _heatPeriod) { // if we're in the cooling state
      performAggregation(convertPPM(getRawValue()));
    }
    // otherwise, we´re currently heating, and should continue to do so
  }
  void manageStateOnlyHeating(unsigned long currentTime) { // a replacement "manage state" method, used for testing purposes
    if (_cycleStartTime == 0) { // if we have not yet started a cycle
      startNewCycle(currentTime); // sets cycle time and starts the cycle by heating 
      heat();
    }
    unsigned long cycleProgress = currentTime - _cycleStartTime;

    if (cycleProgress >= cycleTime) { // if we're done with a measurement cycle
      canGetNewValue = true;
      startNewCycle(currentTime);
    }
    else {
      performAggregation(convertPPM(getRawValue()));
    }
  }
};

class SensorMQ135 : public SensorMQ {
protected:
    double _ppmFresh = 413; // PPM atmospheric CO2 as per April 2019
public:
  SensorMQ135(int pinData, int pinDigital) : SensorMQ(pinData, pinDigital, -1) { 
    sensorName = String("MQ135");
    cycleTime = 10000; // we aggregate raw measurements every 10 seconds
    _analogMeasurementFresh = 32.03519;
    _rRatioFresh = 3.7;
    _funcConstantA = 5.1633;
    _funcConstantB = -0.3495;
    _rl = 20000;
    // _r0 = (convertRS(convertVRL(_analogMeasurementFresh))) / _rRatioFresh; // Calculate R0 with datasheet-fresh-r-ratio-dependency
    _r0 = convertRS(convertVRL(_analogMeasurementFresh)) / (_funcConstantA * pow(_ppmFresh, _funcConstantB)); // calculate R0 with known PPM instead of r-ratio
    _aPink = 0.0003051;
    _bPink = -0.024;
    _cPink = 1.272;
    _aBlue = 0.0003384;
    _bBlue = -0.027;
    _cBlue = 1.399;
  }
  void manageState(unsigned long currentTime) {   
    if (_cycleStartTime == 0) { // if we have not yet started a cycle
      startNewCycle(currentTime); // sets cycle time and starts the cycle by heating 
      heat();
    }
    unsigned long cycleProgress = currentTime - _cycleStartTime;

    if (cycleProgress >= cycleTime) { // if we're done with a measurement cycle
      canGetNewValue = true;
      startNewCycle(currentTime);
    }
    else {
      // Serial.println(String(_r0, 4));
      performAggregation(convertPPM(getRawValue()));
    }
  }
};

class SensorAM2302 : public Sensor {
protected:
  AM2302 _localAM2302;
public:  
  double getRawValue() {
    _localAM2302.readHumidity();
    _localAM2302.readTemperature();
    return 0;    
  }
  int _numMeasurements = 0;
  float _aggHumidity = 0;
  float _aggTemperature = 0;
  void calibrate() { 
    _localAM2302.begin();
  }
  String getValue() {
    float humidity = _aggHumidity/_numMeasurements;
    float temperature = _aggTemperature/_numMeasurements;
    _aggHumidity = 0;
    _aggTemperature = 0;
    _numMeasurements = 0;
    canGetNewValue = false;
    return String(String(humidity) + "|" + String(temperature));
  }
  SensorAM2302(int pinDigital) : _localAM2302(pinDigital) {
    sensorName = String("AM2302");
    canGetNewValue = false;
    cycleTime = 10000;
    _cycleStartTime = 0;
  }
  void startNewCycle(unsigned long newCycleStartTime) {
    _cycleStartTime = newCycleStartTime;
  }
  void manageState(unsigned long currentTime) {
    if (_cycleStartTime == 0) { // if we have not yet started a cycle
      startNewCycle(currentTime); // sets cycle time and starts the cycle by heating 
    }
    
    getRawValue(); // populates the ".humidity" and ".temperature_C" fields
    if (isnan(_localAM2302.humidity) || isnan(_localAM2302.temperature_C)) {
      return;
    }
    else {
      _aggHumidity += _localAM2302.humidity;
      _aggTemperature += _localAM2302.temperature_C;
      _numMeasurements++;    
    }

    unsigned long cycleProgress = currentTime - _cycleStartTime;
    if (cycleProgress >= cycleTime) { // if we're done with a measurement cycle
      canGetNewValue = true;
      startNewCycle(currentTime);
    }
  }
};

class Diode {
private: 
  int _pin;
  
public:
  Diode(int pin) {
    _pin = pin;
    pinMode(_pin, OUTPUT);
  }
  void turnOn() {
    digitalWrite(_pin, HIGH);
  }
  void turnOff() {
    digitalWrite(_pin, LOW);
  }
  void toggleState() {
    digitalWrite(_pin, !digitalRead(_pin));
  }
};

//////////////////////////////////////////////////////////////////////////////////////////////////
// Methods

void delaySec(int sec)
{
  if(sec == 0) return;
  delay(1000);
  Serial.print("Waiting... ");
  Serial.println(sec);
  delaySec(sec - 1);
}

void serialPrintResult(String gasName, double value) {
  serialPrintResult(gasName, String(value));
}

void serialPrintResult(String gasName, String value) {
  Serial.println(String(gasName + ":" + value));
}

//////////////////////////////////////////////////////////////////////////////////////////////////
// Program flow

Diode diode = Diode(13);
SensorMQ7 sensorMQ7 = SensorMQ7(A4, -1, 6);
SensorMQ135 sensorMQ135 = SensorMQ135(A5, -1);
SensorAM2302 sensorAM2302 = SensorAM2302(7);

unsigned long startTime;
unsigned long currentTime;

void setup()
{
  Serial.begin(9600); // initialize serial connection with a data rate of 9600 bits per second
  startTime = millis(); // get number of milliseconds since the program started
  sensorMQ7.calibrate();
  sensorMQ135.calibrate();
  sensorAM2302.calibrate();
}

void loop()
{
  delay(1000);
  currentTime = millis(); 

  // Get new value
  if (sensorMQ7.canGetNewValue) {
    serialPrintResult(String("CO"), sensorMQ7.getValue());
  }
  if (sensorMQ135.canGetNewValue) {
    serialPrintResult(String("CO2"), sensorMQ135.getValue());
  }
  if (sensorAM2302.canGetNewValue) {
    String val = sensorAM2302.getValue();
    int delimiter = val.indexOf("|");
    double hum = val.substring(0, delimiter).toDouble(); 
    double temp = val.substring(delimiter + 1).toDouble();
    serialPrintResult(String("Temp"), temp);
    serialPrintResult(String("Hum"), hum);

    sensorMQ7.setTemperatureHumidity(temp, hum);
    sensorMQ135.setTemperatureHumidity(temp, hum);
  }

  // Manage state
  // sensorMQ7.manageStateOnlyHeating(currentTime);
  sensorMQ7.manageState(currentTime);
  sensorMQ135.manageState(currentTime);
  sensorAM2302.manageState(currentTime);

//  Serial.println("MQ7:" + String(sensorMQ7.getRawValue()));
//  Serial.println("MQ135:" + String(sensorMQ135.getRawValue()));
}
