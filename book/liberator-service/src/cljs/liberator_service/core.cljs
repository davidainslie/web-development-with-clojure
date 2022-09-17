;---
; Excerpted from "Web Development with Clojure, Third Edition",
; published by The Pragmatic Bookshelf.
; Copyrights apply to this code. It may not be used to create training material,
; courses, books, articles, and the like. Contact us if you are in doubt.
; We make no guarantees that this code is fit for any purpose.
; Visit http://www.pragmaticprogrammer.com/titles/dswdcloj3 for more book information.
;---
(ns liberator-service.core
  (:require
   [ajax.core :refer [GET POST]]
   [reagent.core :as reagent :refer [atom]]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [clerk.core :as clerk]
   [accountant.core :as accountant]))

;; -------------------------
;; Views

(defn error-component []
  (when-let [error (session/get :error)]
    [:p error]))

(defn item-list [items]
  (when (not-empty items)
    [:ul
     (for [item items]
       ^{:key item}
       [:li item])]))

(defn parse-items [items]
  (->> items
       clojure.string/split-lines
       (remove empty?)
       vec))

(defn get-items []
  (GET "/items"
    {:error-handler
     #(session/put! :error (:response %))
     :handler
     #(session/put! :items (parse-items %))}))

(defn add-item! [item]
  (session/remove! :error)
  (POST "/items"
    {:headers {"x-csrf-token"
               (.-value (.getElementById js/document "__anti-forgery-token"))}
     :format :raw
     :params {:item (str @item)}
     :error-handler #(session/put! :error (:response %))
     :handler #(do
                 (println "updating")
                 (session/update-in! [:items] conj @item)
                 (reset! item nil))}))

(defn item-input-component []
  (let [item (atom nil)]
    (fn []
      [:div
       [:input
        {:type :text
         :value @item
         :on-change #(reset! item (-> % .-target .-value))
         :placeholder "To-Do item"}]
       [:button
        {:on-click #(add-item! item)}
        "Add To-Do"]])))

;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/" :index]
    ["/items"
     ["" :items]
     ["/:item-id" :item]]
    ["/about" :about]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

;; -------------------------
;; Page components

(defn home-page []
  [:div
   [:h2 "To-Do Items"]
   [error-component]
   [item-list (session/get :items)]
   [item-input-component]])

(defn about-page []
  [:div [:h2 "About liberator-service"]
   [:div [:a {:href "#/"} "go to the home page"]]])

;; -------------------------
;; Translate routes -> page components

(defn page-for [route]
  (case route
    :index #'home-page
    :about #'about-page))

;; -------------------------
;; Page mounting component

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div
       [:header
        [:p [:a {:href (path-for :index)} "Home"] " | "
         [:a {:href (path-for :about)} "About liberator-service"]]]
       [page]
       [:footer
        [:p "liberator-service was generated by the "
         [:a {:href "https://github.com/reagent-project/reagent-template"} "Reagent Template"] "."]]])))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (clerk/initialize!)
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (let [match (reitit/match-by-path router path)
            current-page (:name (:data  match))
            route-params (:path-params match)]
        (reagent/after-render clerk/after-render!)
        (session/put! :route {:current-page (page-for current-page)
                              :route-params route-params})
        (clerk/navigate-page! path)
        ))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))