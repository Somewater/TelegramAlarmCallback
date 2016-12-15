# TelegramAlarmCallback Plugin for Graylog

Send graylog alerts to telegram using [Telegram messenger CLI](https://github.com/vysheng/tg).

**Required Graylog version:** 2.0 and later

Installation
------------

[Download the plugin](https://github.com/Somewater/TelegramAlarmCallback/releases)
and place the `.jar` file in your Graylog plugin directory. The plugin directory
is the `plugins/` folder relative from your `graylog-server` directory by default
and can be configured in your `graylog.conf` file.

Restart `graylog-server` and you are done.

Usage
-----

Specify host:port of telegram client

Build
-----

With docker:

```
docker run -it --rm --name my-maven-project -v "$PWD":/usr/src/mymaven -w /usr/src/mymaven maven:3-jdk-8 mvn clean install
```

