#!/bin/bash -x

# sql/sql_execute.sh

APP=$1
ENV=$2
VERSION=$3
REPORT="/home/aduser/liquibase/release_sql/liquibase.SQL.${APP}.${VERSION}.sql"

if [ $# -lt 3 ]
then
  echo "Example:  ./run_sql.sh APP ENV VERSION"
  echo "Example:  ./run_sql.sh appdirect prod-sam 199.99"
  echo "Example:  ./run_sql.sh jbilling prod-ibm 3.199.99"
  echo "Example:  ./run_sql.sh bulk prod-att 199.99"
  exit
fi

case "${APP}" in
  appdirect)
    echo "appdirect"
    case "${ENV}" in
      prod-att)
        DISTHOST=prod-att-ae1-dist01
        DBNAME=appdirect
        ;;
      prod-att00)
        DISTHOST=prod-att00-dist01
        DBNAME=appdirect
        ;;
      prod-aws00)
        DISTHOST=prod-aws00-dist01
        DBNAME=appdirect
        ;;
      prod-elisa)
        DISTHOST=prod-elisa-hel-dist01
        DBNAME=appdirect
        ;;
      prod-ibm)
        DISTHOST=prod-ibm-dal-dist01
        DBNAME=appdirect
        ;;
      prod-rackspace)
        DISTHOST=prod-rax-build
        DBNAME=appdirect
        ;;
      prod-sam00)
        DISTHOST=prod-sam00-dist01
        DBNAME=appdirect_sam
        ;;
      prod-de00)
        DISTHOST=prod-de00-dist01
        DBNAME=appdirect
        ;;
      prod-ie00)
        DISTHOST=prod-ie00-dist01
        DBNAME=appdirect
        ;;
      prod-ire00)
        DISTHOST=prod-ire00-dist01
        DBNAME=appdirect
        ;;
      prod-kor00)
        DISTHOST=prod-kor00-distribution01
        DBNAME=appdirect
        ;;
      prod-swiss00)
        DISTHOST=prod-swiss00-dist01
        DBNAME=appdirect
        ;;
      prod-telstra)
        DISTHOST=prod-tel-ap2-dist01
        DBNAME=appdirect
        ;;
      prod-comcast00)
        DISTHOST=prod-comcast00-dist01
        DBNAME=appdirect
        ;;
      stage-aws)
        DISTHOST=stage-aws-ae1-dist02
        DBNAME=appdirectstage0
        ;;
    esac ;;
  
  jbilling)
    echo "jbilling"
    case "${ENV}" in
      prod-aws00)
        DISTHOST=prod-aws00-dist01
        DBNAME=jbilling
        ;;
      prod-elisa)
        DISTHOST=prod-elisa-hel-dist01
        DBNAME=jbilling
        ;;
      prod-ibm)
        DISTHOST=prod-ibm-dal-dist01
        DBNAME=jbilling
        ;;
      prod-sam00)
        DISTHOST=prod-sam00-dist01
        DBNAME=jbilling_sam
        ;;
      prod-de00)
        DISTHOST=prod-de00-dist01
        DBNAME=jbilling
        ;;
      prod-ire00)
        DISTHOST=prod-ire00-dist01
        DBNAME=jbilling
        ;;
      prod-kor00)
        DISTHOST=prod-kor00-distribution01
        DBNAME=jbilling
        ;;
      prod-swiss00)
        DISTHOST=prod-swiss00-dist01
        DBNAME=jbilling
        ;;
      prod-telstra)
        DISTHOST=prod-tel-ap2-dist01
        DBNAME=jbilling
        ;;
      prod-comcast00)
        DISTHOST=prod-comcast00-dist01
        DBNAME=jbilling
        ;;
      stage-aws)
        DISTHOST=stage-aws-ae1-dist02
        DBNAME=billingstage0
        ;;
    esac ;;
  
  bulk)
    echo "bulk"
    case "${ENV}" in
      prod-att)
        DISTHOST=prod-att-ae1-dist01
        DBNAME=bulk
        ;;
      prod-att00)
        DISTHOST=prod-att00-dist01
        DBNAME=bulk
        ;;
    esac ;;
esac

ssh aduser@${DISTHOST} "mysql -D ${DBNAME} < ${REPORT}"

ssh aduser@${DISTHOST} "mv ${REPORT} ${REPORT}.done"
