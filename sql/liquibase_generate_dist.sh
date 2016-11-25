#!/bin/bash

# sql/liquibase_generate_dist.sh

export LIQUIBASE_HOME=/home/aduser/liquibase/

# arguments
EXPECTED_ARGS=2
if [ $# -ne ${EXPECTED_ARGS} ]; then
  echo "Usage  : `basename $0` <application> <version>"
  echo "Example: `basename $0` appdirect 190  # This is for appdirect DB"
  echo "Example: `basename $0` jbilling 190   # This is for jbilling DB"
  echo "Example: `basename $0` bulk 190       # This is for bulk DB"
  exit
fi
APP=$1
VERSION=$2

# other variables
BASE_DIR="/home/aduser/liquibase/xml"
REPORT="liquibase.SQL.${APP}.${VERSION}.sql"
SQL_DIR="/home/aduser/liquibase/release_sql"
CONF_DIR="/home/aduser/liquibase/conf"

# building the command line
APP_DIR="${BASE_DIR}/${APP}"
case "${APP}" in
  appdirect)
    LOG_FILE="${APP_DIR}/db/db.changelog-master.xml"
    ;;
  bulk)
    LOG_FILE="${APP_DIR}/liquibase/db.changelog-master.xml"
    ;;
  jbilling)
    LOG_FILE="${APP_DIR}/jbilling-changeLog.xml"
    ;;
esac
CMD="cd ${APP_DIR}; /usr/bin/liquibase --logLevel=severe --defaultsFile=${CONF_DIR}/${APP}.conf --changeLogFile=${LOG_FILE} updateSQL > ${SQL_DIR}/${REPORT} 2>&1"

# execute
echo "`hostname` - Getting SQL for ${APP} DB for Release ${VERSION}"
eval ${CMD}

#EMAIL="gary.barker@appdirect.com,kdonne.chick@appdirect.com,chhaya.patel@appdirect.com,sneha.agnihotri@appdirect.com"
EMAIL="joan.roch@appdirect.com"
mail -s "`hostname` - Liquibase Changes in ${APP} for Release ${VERSION}" ${EMAIL} < ${SQL_DIR}/${REPORT}
