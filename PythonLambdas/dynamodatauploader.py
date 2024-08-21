###############################################################
######################## Configuration ########################
###############################################################
DYNAMO_DATATABLE_NAME = "" # insert your DynamoDB TOKENS table name
DYNAMO_DATATABLE_PRIMARYKEY = "uid"

DETAILED_LOGGING = True # Change this if you only want logs reporting user sign in + token success, or failure.

###############################################################
########################  Source Code  ########################
###############################################################

import boto3 # AWS handler
import logging
import json
import random, string
import datetime



# # # # GLOBAL VARS # # # #
logger = logging.getLogger()
logger.setLevel(logging.INFO)

DYNAMODB = boto3.client('dynamodb')

def lambda_handler(event, context):
    # # instantiate globals # #
    global DYNAMODB, TOKEN
    
    if DETAILED_LOGGING : logger.info(f'received event: {event}')
    
    # Incoming Event is dict datatype, 
    logger.info(f"event: {event}")
    logger.info(f"Type of event: {type(event)}")
    
    # event['body'] is stored as string in dict, parse into JSON
    body = json.loads(event['body'])
    
    # Generating Unique ID for uid primary key in table
    # Generate datetime - stamp: MMHHddmmYYYY = MinuteMinuteHourHourDayDayMonthMonthYearYearYearYear
    username = body.get("username")
    datetimecode = datetime.datetime.now().strftime("%M%H%d%m%Y")
    randomcode = "".join(random.choice(string.ascii_uppercase + string.ascii_lowercase + string.digits) for _ in range(16))
    uid = "_".join([username, datetimecode, randomcode])
    
    # parse data - need to ensure cast to Strings to deal with any JSON ints/floats
    data = {
        "uid": {'S': uid},
        "username": {'S': str(body.get('username'))},
        "volstart": {'N': str(body.get('volumeStart'))},
        "tmp": {'N': str(body.get('tmp'))},
        "membranearea": {'N': str(body.get('membraneArea'))},
        "mwco": {'N': str(body.get('molWeightCutOff'))},
        "concfactor": {'N': str(body.get('concFactor'))}
    }
    
    if DETAILED_LOGGING: logger.info(f"Uploading model data from {username} with data: {data}")
    
    response = DYNAMODB.put_item(
        Item=data,
        ReturnConsumedCapacity='TOTAL',
        TableName=DYNAMO_DATATABLE_NAME,
    )
    
    logger.info(response)
    
    return None # No data sent out, only internal upload info