;---
; Excerpted from "Web Development with Clojure, Third Edition",
; published by The Pragmatic Bookshelf.
; Copyrights apply to this code. It may not be used to create training material,
; courses, books, articles, and the like. Contact us if you are in doubt.
; We make no guarantees that this code is fit for any purpose.
; Visit http://www.pragmaticprogrammer.com/titles/dswdcloj3 for more book information.
;---
(ns guestbook.db.core-test
  (:require
   [guestbook.db.core :refer [*db*] :as db]
   [java-time.pre-java8]
   [luminus-migrations.core :as migrations]
   [clojure.test :refer :all]
   [next.jdbc :as jdbc]
   [guestbook.config :refer [env]]
   [mount.core :as mount]))

;
(use-fixtures
  :once
  (fn [f]
    (mount/start
     #'guestbook.config/env
     #'guestbook.db.core/*db*)
    (migrations/migrate ["migrate"] (select-keys env [:database-url]))
    (f)))
;

;
(deftest test-users
  (jdbc/with-transaction [t-conn *db* {:rollback-only true}]
    (is (= 1 (db/create-user!* t-conn
                               {:login "foo"
                                :password "password"})))
    (is (= {:login "foo"
            :profile {}}
           (dissoc (db/get-user* t-conn
                    {:login "foo"}) :created_at)))))
;


(deftest test-messages
  (jdbc/with-transaction [t-conn *db* {:rollback-only true}]
    (is (->
         {:name "Bob"
          :message "Hello, World"
          :author nil
          :parent nil}
         (->> (db/save-message! t-conn))
         (select-keys [:name :message])
         (= {:name "Bob"
             :message "Hello, World"})))
    (is (= {:name "Bob"
            :message "Hello, World"}
           (-> (db/get-messages t-conn {})
               (first)
               (select-keys [:name :message]))))))