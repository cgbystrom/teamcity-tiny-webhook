# teamcity-tiny-webhook
TeamCity plugin for sending a webhook for each finished build.

While there are other plugins for TeamCity providing webhooks, none of them are sending enough data.
I tried patching one of them but got fed up with how overdesigned they were (+10 files). This plugin is 150 lines of Java in a single file doing just what I need.

# Installing

 1. Download and place the .zip file to `<TeamCity data directory>/plugins`.
 2. Edit `<TeamCity data directory>/config/main-config.xml` and add:
 ```
 <tiny-webhook>
    <target url="http://myserver.example.com/my/path" />
    <target url="http://anotherserver.example.com/another/path" />
  </tiny-webhook>
 ```
 
 If you want to test the plugin in your server, create an endpoint at http://webhook.info.
 
 Plugin has been tested with TeamCity 10.x and 2017.1.
