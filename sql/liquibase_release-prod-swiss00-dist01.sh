#!/bin/bash

export LIQUIBASE_HOME=/home/aduser/liquibase/

PREFIX="prod-swiss00"
APP=$1
VERSION=$2
DATE=`date "+%Y%m%d"`
REPORT="liquibase.SQL.$APP.$VERSION.sql"

SQL_DIR="/home/aduser/liquibase/release_sql"
CONF_DIR="/home/aduser/liquibase/conf"

if [ $# -lt 2 ]
then
        echo "Usage :   ./liquibase_export.sh APP VERSION"
        echo "Example:  ./liquibase_export.sh appdirect 190  # This is for appdirect DB"
        echo "Example:  ./liquibase_export.sh jbilling 190 # This is for jbilling DB"
        exit
fi

case "$1" in

appdirect)  echo "Getting SQL for AppDirect DB for Release $VERSION"
    TARGET_DIR="/home/aduser/liquibase/xml/appdirect/db"
    CONF=appdirect.conf
    CMD="cd /home/aduser/liquibase/xml/appdirect/ ; /usr/bin/liquibase --logLevel=severe --defaultsFile=$CONF_DIR/$CONF --changeLogFile=$TARGET_DIR/db.changelog-master.xml updateSQL > $SQL_DIR/$REPORT 2>&1"
    ;;
jbilling)  echo  "Getting SQL for JBilling DB for Release $VERSION"
    TARGET_DIR="/home/aduser/liquibase/xml/jbilling/"
    CONF=jbilling.conf
    CMD="cd /home/aduser/liquibase/xml/jbilling/ ; /usr/bin/liquibase --logLevel=severe --defaultsFile=$CONF_DIR/$CONF --changeLogFile=$TARGET_DIR/jbilling-changeLog.xml updateSQL > $SQL_DIR/$REPORT 2>&1"
    ;;
esac

eval $CMD

EMAIL="gary.barker@appdirect.com,kdonne.chick@appdirect.com,chhaya.patel@appdirect.com,operations@appdirect.com"
# mail -s "Liquibase Changes in $1 for $PREFIX Release $VERSION" $EMAIL < $SQL_DIR/$REPORT
