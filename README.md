# SherlockBench API Server

This codebase is the server-side for the SherlockBench AI benchmarking system.

It performs the following functions:
- loads in problem-sets from one or more files
- provides an API through-which a client can take the test
- provides a web-front-end through-which an Administrator can create and manage test runs

## Main Website
The project homepage: https://sherlockbench.com

## Technical info
This app is written in Clojure with the following stack:
- Integrant for state and lifecycle management
- reitit for routing
- Honey SQL for database queries
- hiccup for HTML templating
- HTMX and a bit of ClojureScript for front-end

## Development

Install:
- clojure
- npm
- redis

In `resources/` add a config.edn. This file can just be an empty map `{}` but
here is a more fleshed-out example:
```
{;; these are the extra problem-sets to load in
 :problem-namespaces [extra.classic-problems 
                      extra.ng-problems
                      extra.interrobench-problems]

 ;; When true, anonymous runs are allowed. When false, only pre-existing run IDs can be used.
 :allow-anonymous-runs true

 ;; Define custom sets of problems
 ;; Each problem-set can include problems by tag or name, or both
 :custom-problem-sets {"String Manipulation" {:tags [:sherlockbench.sample-problems/string
                                                     :extra.classic-problems/string]}
                       "My Favorites" {:names [("sherlockbench.sample-problems" "filter consonants and vowels")
                                               ("extra.interrobench-problems" "reverse-string")]}}}

```

Create a PostgreSQL database [like this](http://readtheorg.xylon.me.uk/local_postgres.html#org12d8c14).

Then, make a `env/dev/resources/db-credentials.edn` of the form:
```
{:db-name "my_app"
 :user "my_app"
 :password "password"}
```

Now migrate your database. Fire up repl and run this in your `user` namespace:
```
(go)
(migrate)
```

If you want to use the web front-end, add an admin user:
```
(query (q/create-user "testuser" "testpass"))
```

## Deployment

This is an example guide on how to deploy this app onto Debian.

### Compile
Compile your js:
```
npx shadow-cljs release app
```

Compile your .jar:
```
clj -T:build uber
```

### Server
You need a Debian server with these packages installed from apt:
- postgresql
- redis
- default-jre
- nginx

### DB
Create a postgres user and database.

### Application
Create a system user "deploy".

Make these directories and chown them to deploy
- /var/sherlockbench
- /var/log/sherlockbench

Upload the .jar you compiled to /var/sherlockbench.

Add a systemd config at `/lib/systemd/system/sherlockbench.service`:
```
[Unit]
Description=Sherlockbench
After=network.target postgresql.service

[Service]
WorkingDirectory=/var/sherlockbench
SuccessExitStatus=143
ExecStart=/usr/bin/java -jar /var/sherlockbench/sherlockbench.jar run-app
User=deploy

[Install]
WantedBy=multi-user.target
```

Add the environment config to /var/sherlockbench/env.edn
```
{:db-name "sherlockbench"
 :user "sherlockbench"
 :password "EDITME"}
```

### Run the migrations and create a Sherlockbench login
```
sudo -u deploy java -jar /var/sherlockbench/sherlockbench.jar migrate
sudo -u deploy java -jar /var/sherlockbench/sherlockbench.jar add-user
```

### Nginx
Include this snippet in your Nginx vhost:
```
location / {
    proxy_pass http://127.0.0.1:3001/;
    proxy_set_header Host $http_host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_redirect  off;
}
```

### Start everything up

Enable and start your sherlockbench systemd service.

Reload Nginx.

### Log into the UI

Now it should be accessible on the web. Log in and go to the settings page to
create your first pages.

## License

Copyright © Joseph Graham
