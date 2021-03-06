#!/usr/bin/env jruby
#
# sql/liquibase_master_generation.rb

require 'rubygems'
gem 'jruby-openssl'

require 'artifactory'
require 'json'
require 'net/scp'
require 'net/ssh'
require 'net/ssh/gateway'
require 'rest-client'
require 'rubygems'
require 'uri/generic'

Artifactory.configure { |config|
  config.endpoint = 'http://artifactory.appdirectondemand.com/artifactory'
  if ENV['http_proxy']
    proxy_uri = URI.parse(ENV['http_proxy'])
    config.proxy_address = proxy_uri.hostname
    config.proxy_port = proxy_uri.port
    config.proxy_username = proxy_uri.user
    config.proxy_password = proxy_uri.password
  end
}

class ArtifactorySearch
  def initialize(artifact)
    @results = search_latest_branch_build(artifact)
    @build_number = @results[0].properties['build.number'][0] if @results and @results[0].properties['build.number']
    @commit_id_full = @results[0].properties['vcs.revision'][0] if @results and @results[0].properties['vcs.revision']
    @commit_id = @commit_id_full[0..6] if @commit_id_full
  end

  def latest_build(results)
    results.map { |element| element.properties['build.number'].first.to_i }.max
  end

  def keep_latest(results, artifact)
    build_number = latest_build(results)
    results.select { |element| element.properties['build.number'].first.to_i == build_number && element.uri =~ /.*#{artifact.artifact_id}-([0-9]+(.[0-9]+)*)((-PR[0-9]+)?-(SNAPSHOT|[0-9]+\.[0-9]+-[0-9]+)?)?\.#{artifact.packaging}/ }
  end

  def search_latest_branch_build(artifact)
    keep_latest(Artifactory::Resource::Artifact.property_search(projectName: artifact.project, gitBranch: artifact.branch), artifact)
  end

  def download(path, name)
    raise "Cannot find artifact in results: #{@results.map { |a| a.uri }.join(', ')}" unless @results
    FileUtils::mkdir_p path
    @results[0].download(path, { :filename => name })
  end

  def build_number
    @build_number
  end

  def commit_id_full
    @commit_id_full
  end

  def commit_id
    @commit_id
  end

  private :latest_build, :keep_latest, :search_latest_branch_build
end

class DistHost
  def initialize(host, ping_only = false,  port = '22', user = 'aduser', key = File.join(Dir.home, '.ssh', 'jenkins-appdirect'))
    @host = host
    @ping_only = ping_only
    @port = port
    @user = user
    @key = key
  end

  def host
    @host
  end

  def ping_only
    @ping_only
  end

  def port
    @port
  end

  def user
    @user
  end

  def key
    @key
  end
end

class EndpointPool
  class Endpoint
    def initialize(dist_host, scheme, host, ping_host, port, path, artifact_local_path, local_user, key_file, scp_port = 22)
      @dist_host = dist_host
      @scheme = scheme
      @host = host
      @ping_host = ping_host
      @port = port
      @path = path
      @artifact_local_path = artifact_local_path
      @local_user = local_user
      @key_file = key_file
      @scp_port = scp_port
    end

    def dist_host
      @dist_host
    end

    def url
      URI::Generic.build(scheme: @scheme, host: @ping_host || @host, port: @port, path: @path)
    end

    def scp_url
      URI::Generic.build(scheme: 'scp', host: @host, port: @scp_port, path: @artifact_local_path)
    end

    def local_user
      @local_user
    end

    def key_file
      @key_file
    end
  end

  def initialize(scheme, nodes, port, path, artifact_local_path, dist_host = nil, ping_host = nil?,
                 local_user = 'aduser', key_file = File.join(Dir.home, '.ssh', 'jenkins-appdirect'), scp_port = 22)
    @scheme = scheme
    @nodes = nodes
    @port = port
    @path = path
    @artifact_local_path = artifact_local_path
    @dist_host = dist_host
    @ping_host = ping_host
    @local_user = local_user
    @key_file = key_file
    @scp_port = scp_port
  end

  def each(&block)
    @nodes.collect { |node| Endpoint.new(@dist_host, @scheme, node, @ping_host, @port, @path, @artifact_local_path, @local_user, @key_file) }.each(&block)
  end

  def nodes
    @nodes
  end

  def dist_host
    @dist_host
  end
end

class ArtifactDefinition
  def initialize(project, artifact_id, classifier, packaging, branch)
    @project = project
    @artifact_id = artifact_id
    @classifier = classifier
    @packaging = packaging
    @branch = branch
  end

  def project
    @project
  end

  def artifact_id
    @artifact_id
  end

  def classifier
    @classifier
  end

  def packaging
    @packaging
  end

  def filename
    "#{@artifact_id}-#{@classifier}.#{@packaging}"
  end

  def branch
    @branch
  end
end

def tunnel_connection(dist_host, url, &block)
  if dist_host.nil?
    puts '      o No dist host defined, not tunneling connection'
    block.call(url)
  else
    gateway = Net::SSH::Gateway.new(dist_host.host, dist_host.user, keys: [ dist_host.key ])
    puts "      o Using gateway #{dist_host.host} with user #{dist_host.user}"
    result = gateway.open(url.host, url.port) do |port|
      puts "      o Tunnel open to #{url.host}:#{url.port} with local port #{port}"
      tunneled_url = URI::Generic.build(scheme: url.scheme, host: '127.0.0.1', port: port, path: url.path)
      block.call(tunneled_url)
    end
    gateway.shutdown!
    puts "      o Gateway shut down"

    result
  end
end

def upload_war_without_dist_host(war, endpoint)
  puts "  - Uploading without dist host"
  Net::SCP.start(endpoint.scp_url.host, endpoint.local_user, keys: [ endpoint.key_file ], port: endpoint.scp_url.port) do |scp|
    puts "  - Uploading WAR to #{endpoint.scp_url}"
    scp.upload!(war, endpoint.scp_url.path)
  end
end

def upload_war_with_dist_host(war, endpoint)
  puts "  - Uploading with dist host"
  dist_host = endpoint.dist_host

  Net::SSH.start(dist_host.host, dist_host.user, keys: [ dist_host.key ]) do |ssh|
    puts "  - SSH connection opened to #{dist_host.host}"
    scp_command = "scp #{'-i ' + endpoint.key_file if endpoint.key_file} #{war} #{endpoint.local_user}@#{endpoint.scp_url.host}:#{endpoint.scp_url.path}"
    puts "    - Executing scp command: #{scp_command}"
    ssh.exec!(scp_command)
  end

end

def dist_host_war(war)
  "#{ENV['BUILD_ID']}-#{File.basename(war)}"
end

def clean_dist_host_war(war, ping_endpoint_pool)
  dist_host = ping_endpoint_pool.dist_host
  unless dist_host.nil? or dist_host.ping_only
    Net::SSH.start(dist_host.host, dist_host.user, keys: [ dist_host.key ]) do |ssh|
      puts "  - SSH connection opened to #{dist_host.host}"
      rm_war_command = "rm -rf #{dist_host_war(war)}"
      puts "    + Executing war cleanup command: #{rm_war_command}"
      ssh.exec!(rm_war_command)
    end
  end
end

def prepare_upload(war, ping_endpoint_pool)
  unless ping_endpoint_pool.dist_host.nil? or ping_endpoint_pool.dist_host.ping_only
    Net::SCP.start(ping_endpoint_pool.dist_host.host, ping_endpoint_pool.dist_host.user, keys: [ ping_endpoint_pool.dist_host.key ], port: ping_endpoint_pool.dist_host.port) do |scp|
      puts "  - Uploading WAR to #{ping_endpoint_pool.dist_host.host}"
      scp.upload!(war, dist_host_war(war))
    end
  end
  puts "  - Ready to upload"
end

def upload_war(war, endpoint)
  if endpoint.dist_host.nil? or endpoint.dist_host.ping_only
    upload_war_without_dist_host war, endpoint
  else
    upload_war_with_dist_host dist_host_war(war), endpoint
  end
end

def deploy_war(war, ping_endpoint_pool, commit)
  puts "* Deploying war file #{war}:"
  if ping_endpoint_pool.dist_host
    puts "  - Using dist host #{ping_endpoint_pool.dist_host.host}"
  end

  prepare_upload(war, ping_endpoint_pool)

  # Update all nodes in sequence
  ping_endpoint_pool.each do |endpoint|
    puts "  - Processing #{endpoint.url.host}"

    # Upload WAR
    upload_war(war, endpoint)

    # Wait for node to restart
    puts "  - Waiting for commit #{commit} to be available on node #{endpoint.url.host}"
    attempts = 1
    max_attempts = 15
    begin
      if attempts > max_attempts
        puts "    + Giving up on node #{endpoint.url.host} after #{attempts}"
        raise "Cannot start node #{endpoint.host}"
      end

      sleep(20.0)
      puts "    + Checking tomcat status on #{endpoint.url.host}: attempt #{attempts} / #{max_attempts}"
      attempts = attempts + 1
    end until alive?(endpoint, commit)
  end
end

def deploy_branch(artifact, ping_endpoint_pool)
  puts "* Deploying WAR to #{ping_endpoint_pool.nodes.join(', ')}"
  begin
    # initialization
    target_dir = 'target'

    # clean up
    FileUtils::rm_rf(target_dir)

    # Download artifact
    puts "* Searching for #{artifact.packaging} package"
    search = ArtifactorySearch.new(artifact)
    puts "* Downloading #{artifact.packaging} package"
    package_filename = artifact.filename
    search.download(target_dir, package_filename)

    # Deploy artifact
    file_path = File.join(target_dir, package_filename)
    deploy_war(file_path, ping_endpoint_pool, search.commit_id)
    clean_dist_host_war(artifact.filename, ping_endpoint_pool)
    puts "Success"
  rescue => e
    # Catch all errors and fail the build
    puts "* Error: #{e.message}"
    e.backtrace.each do |trace|
      puts "  - #{trace}"
    end

    clean_dist_host_war(artifact.filename, ping_endpoint_pool)

    puts "Failure"
    raise "Build failure"
  end
end

def alive?(endpoint, commit)
  begin
    # Ping health check URL
    content = tunnel_connection(endpoint.dist_host, endpoint.url) do |url|
      puts "      o Calling health check URL on #{endpoint.url} (#{url})"
      RestClient::Request.execute(:url => url.to_s, :method => :get, :verify_ssl => false)
    end

    # Check health check content
    json = JSON.parse(content)
    node_commit = json['git']['commit']['id']
    if commit == node_commit
      puts "        . Node #{endpoint.url.host} started"
      true
    else
      puts "        . Node #{endpoint.url.host} still running commit #{node_commit} instead of #{commit}"
      false
    end
  rescue => e
    puts "        . Node #{endpoint.url.host} not fully started yet: #{e.message}"
    false
  end
end

def generate_prod_sql(project, branch)
  puts ""
  puts "Generate Production SQL for #{project} using branch #{branch}"
  puts "----------------------"

  environments_map = {
    "prod-staging": {
      dist_host: DistHost.new('52.90.138.131')
    },
    "prod-att": {
      dist_host: DistHost.new('prod-att-ae1-dist01')
    },
    "prod-aws-de": {
      dist_host: DistHost.new('52.28.6.22')
    },
    "prod-aws-kor": {
      dist_host: DistHost.new('prod-kor00-distribution01')
    },
    "prod-aws-us": {
      dist_host: DistHost.new('54.84.27.181')
    },
    "prod-comcast": {
      dist_host: DistHost.new('52.54.110.80')
    },
    "prod-elisa": {
      dist_host: DistHost.new('193.66.8.195')
    },
    "prod-ibm": {
      dist_host: DistHost.new('184.172.109.235')
    },
    "prod-swisscom": {
      dist_host: DistHost.new('193.246.34.223')
    },
    "prod-swisscom-01": {
      dist_host: DistHost.new('193.246.39.138')
    },
    "prod-telstra": {
      dist_host: DistHost.new('54.206.33.245')
    }
  }

  projects_map = {
    "appdirect": {
      environments: ["prod-staging", "prod-att", "prod-aws-de", "prod-aws-kor", "prod-aws-us",
                     "prod-comcast", "prod-elisa", "prod-ibm", "prod-swisscom", "prod-swisscom-01", "prod-telstra"]
    },
    "bulk": {
      environments: ["prod-att"]
    },
    "jbilling": {
      environments: ["prod-staging", "prod-aws-de", "prod-aws-kor", "prod-aws-us", "prod-comcast",
                     "prod-elisa", "prod-ibm", "prod-swisscom", "prod-swisscom-01", "prod-telstra"]
    }
  }

  project = projects_map[project.to_sym]
  if project.nil?
    puts " * Unsupported project #{project}"
    puts "Failure"
    raise "Unsupported environment #{project}"
  end

  ping_endpoint_pool = EndpointPool.new(env.fetch(:scheme, 'http'), env[:nodes], 8443, '/info', '/home/aduser/tomcat/jbilling.war', env[:dist_host])
  artifact = ArtifactDefinition.new('jbilling', 'jbilling', 'war', branch)

  deploy_branch(artifact, ping_endpoint_pool)
end


deploy_jbilling_branch_to_prod 'jbilling', ENV['billingVersion'], ENV['Environment']
