#!/bin/bash

# sql/liquibase_generate_master.sh

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
SCRIPT_DIR=`dirname $0`
BASE_DIR=${SCRIPT_DIR}/..
TIMESTAMP=`date '+%Y%m%d_%H%M%S'`
DATE=`date -I`
LOGFILE_DEST=/home/aduser/liquibase/logs
LOGFILE=${LOGFILE_DEST}/liquibase.${APP}.${VERSION}.${TIMESTAMP}.log
TMPFILE=`/bin/mktemp`

# set directories
BASE_GIT_DIR="/home/aduser/liquibase/source"
BASE_LIQUIBASE_DIR="/home/aduser/liquibase/xml"
case "${APP}" in
  appdirect)  echo "Copying XML for AppDirect DB for Release ${VERSION}"
    GIT_DIR="${BASE_GIT_DIR}/AppDirect"
    XML_DIR="${BASE_GIT_DIR}/AppDirect/appdirect-parent/appdirect-model/src/main/resources/db"
    TARGET_DIR="${BASE_LIQUIBASE_DIR}/appdirect/db"
    DIST_HOSTS=(prod-att-ae1-dist01 prod-aws00-dist01 prod-comcast00-dist01 prod-de00-dist01 prod-elisa-hel-dist01 prod-ibm-dal-dist01 prod-kor00-distribution01 prod-rax-build prod-swiss00-dist01 prod-tel-ap2-dist01 stage0-aws-ae1-dist02)
    ;;
  bulk)  echo  "Copying XML for Bulk DB for Release ${VERSION}"
    GIT_DIR="${BASE_GIT_DIR}/ad-att-standalone"
    XML_DIR="${BASE_GIT_DIR}/ad-att-standalone/ad-att-standalone-lib/src/main/resources/liquibase"
    TARGET_DIR="${BASE_LIQUIBASE_DIR}/bulk/liquibase"
    DIST_HOSTS=(prod-att-ae1-dist01)
    ;;
  jbilling)  echo  "Copying XML for JBilling DB for Release ${VERSION}"
    GIT_DIR="${BASE_GIT_DIR}/jbilling"
    XML_DIR="${BASE_GIT_DIR}/jbilling/web-app/database"
    TARGET_DIR="${BASE_LIQUIBASE_DIR}/jbilling"
    DIST_HOSTS=(prod-aws00-dist01 prod-comcast00-dist01 prod-de00-dist01 prod-elisa-hel-dist01 prod-ibm-dal-dist01 prod-kor00-distribution01 prod-swiss00-dist01 prod-tel-ap2-dist01 stage0-aws-ae1-dist02)
    ;;
esac

#::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
# func_log(): Update Log File
#
# IN: Log message to append to LOGFILE
# IN: Y - Start LOGFILE
#::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
func_log() {
  if [ ! -d ${LOGFILE_DEST} ]; then
    mkdir ${LOGFILE_DEST}
  fi

  echo "`date '+%Y-%m-%d %H:%M:%S'` ${APP}"
  if [ ${#2} -gt 0 ]; then
    echo "!!!!!!!!!!!!!!!!!!!!!!!!!!ERROR!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
  fi
  if [ ! -f ${LOGFILE} ]; then
    echo "`date '+%Y-%m-%d %H:%M:%S'` ${APP}" > ${LOGFILE} 2>&1
  else
    echo "`date '+%Y-%m-%d %H:%M:%S'` ${APP}" >> ${LOGFILE} 2>&1
  fi
  if [ ${#2} -gt 0 ]; then
    echo "!!!!!!!!!!!!!!!!!!!!!!!!!!ERROR!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    echo "Check ${LOGFILE} for more information!"
    exit
  fi
}

#::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
# func_git_pull(): Get git version
#::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
func_git_pull() {
  func_log "Checking out ${VERSION} source from GIT..."
  cd ${GIT_DIR} >> ${LOGFILE} 2>&1

  git reset --hard >> ${LOGFILE} 2>&1
  if [ ${?} -ne 0 ]; then
    func_log "git reset --hard failed!" "Y"
  else
    func_log "git reset --hard successful"
  fi

  git clean -df >> ${LOGFILE} 2>&1
  if [ ${?} -ne 0 ]; then
    func_log "git clean -df failed!" "Y"
  else
    func_log "git clean -df successful"
  fi

  git fetch origin --tags --prune >> ${LOGFILE} 2>&1
  if [ ${?} -ne 0 ]; then
    func_log "git fetch origin failed!" "Y"
  else
    func_log "git fetch origin successful"
  fi

  git checkout refs/tags/"${VERSION}" >> ${LOGFILE} 2>&1
  if [ ${?} -ne 0 ]; then
    func_log "git checkout refs/tags/${VERSION} failed!" "Y"
  else
    func_log "git checkout refs/tags/${VERSION} successful."
  fi
}

#::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
# func_rsync(): Update Log File
#
# IN: Log message to append to LOGFILE
# IN: Y - Start LOGFILE
#::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
func_rsync() {
  for i in "${DIST_HOSTS[@]}" ; do rsync -aq --delete ${XML_DIR}/ $i:${TARGET_DIR} ; done
  if [ ${?} -ne 0 ]; then
    func_log "rsync of ${VERSION} xml to dist host failed!" "Y"
  else
    func_log "rsync of ${VERSION} xml to dist host successful."
  fi
}

#::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
# func_generate(): Update Log File
#
# IN: Log message to append to LOGFILE
# IN: Y - Start LOGFILE
#::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
func_generate() {
  for i in "${DIST_HOSTS[@]}"; do
    echo $i
    scp liquibase_release.sh $i:/home/aduser/liquibase/bin/liquibase_generate_dist.sh
    #ssh $i bash -x /home/aduser/liquibase/bin/liquibase_generate_dist.sh ${APP} ${VERSION} >> ${TMPFILE} 2>&1
  done
  #/bin/cat ${TMPFILE} | /bin/mail -s "Release SQL generated for ${APP} ${VERSION}" operations@appdirect.com
  /bin/rm ${TMPFILE}
}

######################
#
# call all the functions
#
######################
func_git_pull
func_rsync
func_generate
