__author__ = 'pierre.lacerte'

import urllib2
import json
import sys
import os
import logging
import re

prod_maven_profile_matrix = {
    "prod-staging": "stage-www",
    "prod-att": "prod-att-www",
    "prod-aws-de": "prod-de00-www",
    "prod-aws-ie": "prod-ie00-www",
    "prod-aws-kor": "prod-kor00-www",
    "prod-aws-us": "prod-aws00-www",
    "prod-aws-west-dr": "prod-aws01-www",
    "prod-comcast": "prod-comcast00-www",
    "prod-elisa": "prod-elisa-www",
    "prod-ibm": "prod-ibm-www",
    "prod-ire": "prod-ire00-www",
    "prod-samsung": "prod-sam00-www",
    "prod-swisscom": "prod-swiss00-www",
    "prod-swisscom-01": "prod-swiss01-www",
    "prod-telstra": "prod-tel-www"
}

prod_tomcat_host_matrix = {
    "prod-staging": "stage0-www-origin01 stage0-www-app01 stage0-www-app02",
    "prod-att": "prod_att_www_origin1 prod_att_www_origin2 prod_att_www_origin3 prod_att_www_app1 prod_att_www_app2 prod_att_www_app3",
    "prod-aws-de": "prod-de00-www-origin01 prod-de00-www-origin02 prod-de00-www-app01 prod-de00-www-app02",
    "prod-aws-ie": "prod-ie00-www-origin01 prod-ie00-www-origin02 prod-ie00-www-app01 prod-ie00-www-app02",
    "prod-aws-kor": "prod-kor00-www-origin01 prod-kor00-www-origin02 prod-kor00-www-app01 prod-kor00-www-app02",
    "prod-aws-us": "prod-aws00-www-origin01 prod-aws00-www-origin02 prod-aws00-www-app02 prod-aws00-www-app03 prod-aws00-www-app04 prod-aws00-www-app05 prod-aws00-www-app06 prod-aws00-www-app07",
    "prod-aws-west-dr": "prod-aws01-www-origin01 prod-aws01-www-origin02 prod-aws01-www-app01 prod-aws01-www-app02 prod-aws01-www-app03 prod-aws01-www-app04 prod-aws01-www-app05 prod-aws01-www-app06",
    "prod-comcast": "prod-comcast00-www-origin01 prod-comcast00-www-origin02 prod-comcast00-www-app01 prod-comcast00-www-app02",
    "prod-elisa": "prod-elisa-hel-www-app01 prod-elisa-hel-www-app02",
    "prod-ibm": "prod-ibm-dal-www-origin1 prod-ibm-dal-www-origin2 prod-ibm-dal-www-app1 prod-ibm-dal-www-app2",
    "prod-ire": "prod-ire00-www-origin01 prod-ire00-www-origin02 prod-ire00-www-app01 prod-ire00-www-app02",
    "prod-samsung": "prod-sam00-www-origin01 prod-sam00-www-origin02 prod-sam00-www-app01 prod-sam00-www-app02",
    "prod-swisscom": "prod-swiss00-www-origin01 prod-swiss00-www-origin02 prod-swiss00-www-app01 prod-swiss00-www-app02",
    "prod-swisscom-01": "prod-swiss01-www-origin01 prod-swiss01-www-origin02 prod-swiss01-www-app01 prod-swiss01-www-app02",
    "prod-telstra": "prod-tel-ap2-www-origin01 prod-tel-ap2-www-origin02 prod-tel-ap2-www-app01 prod-tel-ap2-www-app02 prod-tel-ap2-www-app03 prod-tel-ap2-www-app04 prod-tel-ap2-www-app05"
}

prod_dist_host_matrix = {
    "prod-staging": "stage0-aws-ae1-dist02",
    "prod-att": "prod-att-ae1-dist01",
    "prod-aws-de": "prod-de00-dist01",
    "prod-aws-ie": "prod-ie00-dist01",
    "prod-aws-kor": "prod-kor00-distribution01",
    "prod-aws-us": "prod-aws00-dist01",
    "prod-aws-west-dr": "prod-aws01-dist01",
    "prod-comcast": "prod-comcast00-dist01",
    "prod-elisa": "prod-elisa-hel-dist01",
    "prod-ibm": "prod-ibm-dal-dist01",
    "prod-ire": "prod-ire00-dist01",
    "prod-samsung": "prod-sam00-dist01",
    "prod-swisscom": "prod-swiss00-dist01",
    "prod-swisscom-01": "prod-swiss01-dist01",
    "prod-telstra": "prod-tel-ap2-dist01"
}

prod_health_check_type_matrix = {
    "prod-elisa": "https"
}


def init_logger(logger):
    logger.setLevel(logging.INFO)
    # create console handler
    ch = logging.StreamHandler()
    # create formatter
    formatter = logging.Formatter("%(asctime)s - %(name)s - %(levelname)s - %(message)s")
    # add formatter to ch
    ch.setFormatter(formatter)
    # add ch to logger
    logger.addHandler(ch)


def find_download_link(url):
    if url is None:
        logger.info("URL is None... Cannot download artifact!")
        sys.exit("URL is None... Cannot download artifact!")
    else:
        request = urllib2.Request(url)
        response = urllib2.urlopen(request).read()
        response_json = json.loads(response)
        download_path = response_json['downloadUri']
        write_file('url.out', download_path)


def search(url, search_name, search_value, artifact_id, artifact_type):
    final_url = url + '?' + search_name + '=' + search_value
    logger.info("Generated Artifactory search Url:%s", final_url)
    request = urllib2.Request(final_url, headers={"X-Result-Detail": "properties"})
    response = urllib2.urlopen(request).read()

    logger.info(response)
    response_json = json.loads(response)
    results_json = response_json['results']
    total = len(results_json)
    logger.info(str(total) + " Results found. Now filtering...")
    pattern = "\/" + artifact_id + "[^\/]*?\." + artifact_type
    results_json = filter(lambda x: re.search(pattern, x['uri']) is not None, results_json)
    total = len(results_json)
    logger.info(str(total) + " Results left.")

    if total == 0:
        logger.info('No Universal WAR with this branch name found on Artifactory!')
        sys.exit('No Universal WAR with this branch name found on Artifactory')
    elif total == 1:
        logger.info('Creating alive.out and url.out...')
        path = results_json[0]['uri']
        alive = results_json[0]['properties']['alive'][0]
        write_file('alive.out', str(alive))
        find_download_link(path)
    else:
        logger.info("Multiple results found for search. We will return the most recent one...")
        top = 0
        path = None
        for line in results_json:
            if line['properties']['build.timestamp'] > top:
                top = line['properties']['build.timestamp']
                path = line['uri']
        find_download_link(path)


def find_maven_profile(target_env):
    logger.info('Resolving deployment configuration for %s', target_env)

    if prod_maven_profile_matrix.get(target_env) is None:
        logger.error('No Maven Profile specified for: %s', target_env)
        sys.exit('No Maven Profile Found')
    else:
        maven_profile = prod_maven_profile_matrix.get(target_env)
        logger.info('Maven profile: %s', maven_profile)
        write_file('maven_profile.out', maven_profile)


def find_tomcat_hosts(target_env):
    if prod_tomcat_host_matrix.get(target_env) is None:
        logger.error('No Tomcat Hosts specified for: %s', target_env)
        sys.exit('No Tomcat Hosts Found')
    else:
        tomcat_hosts = prod_tomcat_host_matrix[target_env]
        logger.info('Will push to tomcat hosts: %s', tomcat_hosts)
        write_file('tomcat_hosts.out', tomcat_hosts)


def find_dist_host(target_env):
    if prod_dist_host_matrix.get(target_env) is None:
        logger.info('No Dist Host needed for: %s', target_env)
        write_file('dist_host.out', 'none')
    else:
        dist_host = prod_dist_host_matrix[target_env]
        logger.info('Will use Dist Host: %s', dist_host)
        write_file('dist_host.out', dist_host)


def find_health_check_type(target_env):
    if prod_health_check_type_matrix.get(target_env) is None:
        logger.info('Will health check using HTTP: %s', target_env)
        write_file('health_check.out', 'http')
    else:
        health_check = prod_health_check_type_matrix[target_env]
        logger.info('Will health check using: %s', health_check)
        write_file('health_check.out', health_check)


def write_file(file, content):
    f = open(file, 'w')
    f.write(content)
    f.close()


logger = logging.getLogger("PRODUCTION.DeployConfigGenerator")
init_logger(logger)
target_env = os.environ.get('Environment')
target_env = target_env.lower()

find_maven_profile(target_env)
find_dist_host(target_env)
find_tomcat_hosts(target_env)
find_health_check_type(target_env)

search_name = 'releaseNumber'
search_value = os.environ.get('appdirectVersion')
url = "http://artifactory.appdirect.com/artifactory/api/search/prop"
logger.info('Will search on Artifactory using property name:%s and search string:%s', search_name, search_value)
search(url, search_name, search_value, 'appdirect', 'tar')

logger.info("Done generating configuration. Ready to deploy!")
