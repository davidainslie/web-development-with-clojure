# Guest Book

Generated using Luminus version "3.91":
```shell
lein new luminus guestbook --template-version 3.91 -- +h2 +http-kit
```

We add the `+h2` parameter to indicate that we want to have an instance of the `h2` embedded database initialized, and the `+http-kit` parameter to indicate that we want to use the `http-kit8` web server.
We also specify the version of the template that we want to use explicitly to ensure that the projects you generate are exactly the same as the ones used in this book. Otherwise, Leiningen will default to the latest available version of the luminus template.

## Prerequisites

You will need [Leiningen][1] 2.0 or above installed.

[1]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:
```shell
lein run 
```

Clojure(Script) development is often best done interactively, so bootstrap a REPL:
```shell
lein repl
```
By default we will be bootstrapped into the `user` namespace which is located in `env/dev/clj/user.clj` (which you can navigate to in the sidebar of your IDE).
This namespace contains helper functions for handling various development tasks, including `start` and `stop` functions that allow us to control the state of the application.

## Working with the templated web app example

Delete the migrations:
```shell
rm resources/migrations/*
```

New pairs of migration files can be generated from the REPL by running the create-migration function:
```clojure
user=> (create-migration "guestbook")
```

A common beginner error that you may encounter is forgetting to start the database connection.
If you do get this error, don’t worry, just run `(start)` and try again.
In fact, any error that mentions mount.core.DerefableState is probably due to forgetting to run `(start)`.

In our `up` and `down` migrations we add respectively:
```sql
create table guestbook (
  id integer primary key auto_increment,
  name varchar(30),
  message varchar(200),
  timestamp timestamp default current_timestamp
);

drop table guestbook;
```

and then `migrate`:
```clojure
user=> (migrate)
```

and reload the `h2` in memory database:
```clojure
(restart)
```

We have database queries in [resources/sql/queries.sql](resources/sql/queries.sql). Some conventions:
- function names are specified using `-- :name`, a suffix of `!` implies mutation.
- `-- :doc` for generating metadata documentation.
- `save-message!` has `:!` indicates destructive; `:n` shows row count will be returned.
- `get-messages` has `:?` indicating a query; `:*` states multiple rows returned.

The guestbook.db.core namespace contains a call to the conman.core/bind-connection macro.
This macro reads the SQL queries that we defined and creates Clojure functions.
In conjunction with the macro, the query functions can be run from namespace `guestbook.db.core`:

```clojure
user=> (in-ns 'guestbook.db.core)
```

First reload the query functions:
```clojure
guestbook.db.core=> (conman/bind-connection *db* "sql/queries.sql")
```

then we interact with the database:
```clojure
guestbook.db.core=> (save-message! {:name "Bob" :message "Hello, World"})
1

guestbook.db.core=> (get-messages)
[{:id 1
  :message "Hello, World"
  :name "Bob"
  :timestamp #object[java.time.LocalDateTime 0x9503cd1 "2022-09-17T18:13:44.033"]}]
```