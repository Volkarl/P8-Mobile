# Periodically checks all entries in database (longterm and shortterm)
# If an entry is deemed "too old", it is deleted from the given table
# We consider "too old" as:
#	24 hours, in shortterm table
#	14 days, in longterm

from datetime import datetime, timedelta
import time as t
import sqlite3
from sqlite3 import Error

#agelimit in hours
agelim_short = 24 #hours, -> 1 day
agelim_long = 24*14 #hours, -> 14 days

dbfile = '../data.sqlite'

def main():
	while True:
		maintainShortterm()
		maintainLongterm()
		t.sleep(60*5) # sleep for 5 minutes

def maintainLongterm():
	db = createConnection()
	cursor = db.cursor()
	cursor.execute("SELECT * FROM longterm")
	data = cursor.fetchall()
	for row in data:
		entrytime = datetime.strptime(row[4], '%Y-%m-%d %H:%M:%S.%f')
		#If too old, remove row
		diff = timedelta(entrytime) - timedelta(datetime.now())
		diff = diff.total_seconds() // 3600
		if diff.seconds/3600 > agelim_long:
			id = row[0]
			cur = conn.cursor()
			cursor.execute('DELETE FROM longterm WHERE id=?', (id,))
	db.commit()
	db.close()

def maintainShortterm():
	db = createConnection()
	cursor = db.cursor()
	cursor.execute("SELECT * FROM shortterm")
	data = cursor.fetchall()
	for row in data:
		entrytime = datetime.strptime(row[4], '%Y-%m-%d %H:%M:%S.%f')
		#If too old, remove row
		diff = timedelta(entrytime) - timedelta(datetime.now())
		diff = diff.total_seconds() // 3600
		if diff.seconds/3600 > agelim_short:
			id = row[0]
			cur = conn.cursor()
			cursor.execute('DELETE FROM shortterm WHERE id=?', (id,))
	db.commit()
	db.close()

def createConnection():
    try:
        conn = sqlite3.connect(dbfile)
        return conn
    except Error as e:
        print(e)
    return None


if __name__ == '__main__':
	main()