import os
import time, random
from kafka import KafkaProducer

producer = KafkaProducer(bootstrap_servers='localhost:9092')


def parseFileLine(line):
    line_splitted = line.split(",")

    ticker = line_splitted[0]
    date = line_splitted[1]
    close = line_splitted[2]
    reference = line_splitted[3]
    volume = line_splitted[4]
    turnover = line_splitted[5]
    last = line_splitted[6]
    high = float(line_splitted[7])
    low = float(line_splitted[8])
    average = line_splitted[9]

    value1 = close
    value2 = round(random.uniform(low, high), 4)
    print(ticker + ',' + date + ',' + line_splitted[2])
    return str(ticker + ',' + date + ',' + value1).encode('utf-8')


path = str(os.path.abspath(__file__))[0:-12] + 'IBEX35_Individual_data'

fileANA = os.path.join(path, 'ACCIONA.csv')
fileTEF = os.path.join(path, 'TELEFONICA.csv')
fileACX = os.path.join(path, 'ACERINOX.csv')
infileAna = open(fileANA, 'r')
infileTEL = open(fileTEF, 'r')
infileACX = open(fileACX, 'r')

for i in range(240):
    print(i)
    time.sleep(5)
    try:
        producer.send("IBEX35", parseFileLine(infileAna.readlines(1)[0]))
    except:
        print("First line")
    try:
        producer.send("IBEX35", parseFileLine(infileTEL.readlines(1)[0]))
    except:
        print("First line")
    try:
        producer.send("IBEX35", parseFileLine(infileACX.readlines(1)[0]))
    except:
        print("First line")

infileAna.close()
infileTEL.close()
infileACX.close()
