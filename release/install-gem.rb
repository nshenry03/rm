#!/usr/bin/env jruby
#
# release/install-gem.rb

puts ""
puts "Installing required gems"
puts "------------------------"


gems = [
    { :name => 'archive-tar-minitar', :version => '0.5.2' },
    { :name => 'artifactory', :version => '2.2.1' },
    { :name => 'git', :version => '1.2.9.1' },
    { :name => 'json', :version => '1.8.3' },
    { :name => 'net-scp', :version => '1.2.1' },
    { :name => 'net-ssh', :version => '2.9.2' },
    { :name => 'net-ssh-gateway', :version => '1.2.0' },
    { :name => 'rest-client', :version => '1.8.0' },
    { :name => 'rubyzip', :version => '1.1.7' },
    { :name => 'nokogiri', :version => '1.6.6.2' },
    { :name => 'compass', :version => '1.0.3' },
    { :name => 'bundler', :version => '1.12.5' },
    { :name => 'rspec', :version => '3.5.0' },
    { :name => 'serverspec', :version => '2.36.0' },
    { :name => 'docker-api', :version => '1.29.1' }
]

gems = gems + [ { :name => 'jruby-openssl', :version => '0.9.7' } ]  if RUBY_PLATFORM == "java"

gems.each do |gem|
  begin
    gem gem[:name], ">=#{gem[:version]}"
    puts "* gem #{gem[:name]} >=#{gem[:version]} already installed"
  rescue Gem::LoadError
    puts "* Installing gem #{gem[:name]} >=#{gem[:version]}"
    system "gem install #{gem[:name]} -v #{gem[:version]}"
  end
end

puts "Success"
puts ""
