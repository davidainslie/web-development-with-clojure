;---
; Excerpted from "Web Development with Clojure, Third Edition",
; published by The Pragmatic Bookshelf.
; Copyrights apply to this code. It may not be used to create training material,
; courses, books, articles, and the like. Contact us if you are in doubt.
; We make no guarantees that this code is fit for any purpose.
; Visit http://www.pragmaticprogrammer.com/titles/dswdcloj3 for more book information.
;---
;
(ns guestbook.views.post
  (:require
   [re-frame.core :as rf]
   [reagent.core :as r]
   [cljs.pprint :refer [pprint]]
   [guestbook.messages :as msg]))

(defn clear-post-keys [db]
  (dissoc db ::error ::post))

(rf/reg-event-fx
 ::fetch-post
 (fn [{:keys [db]} [_ post-id]]
   {:db (clear-post-keys db)
    :ajax/get {:url (str "/api/message/" post-id)
               :success-path [:message]
               :success-event [::set-post]
               :error-event [::set-post-error]}}))

(rf/reg-event-db
 ::set-post
 (fn [db [_ post]]
   (assoc db ::post post)))

(rf/reg-event-db
 ::set-post-error
 (fn [db [_ response]]
   (assoc db ::error response)))

(rf/reg-event-db
 ::clear-post
 (fn [db _]
   (clear-post-keys db)))


(rf/reg-sub
 ::post
 (fn [db _]
   (::post db nil)))

(rf/reg-sub
 ::error
 (fn [db _]
   (::error db)))

(rf/reg-sub
 ::loading?
 :<- [::post]
 :<- [::error]
 (fn [[post error] _]
   (and (empty? post) (empty? error))))

;
(rf/reg-event-fx
 ::fetch-replies
 (fn [{:keys [db]} [_ post-id]]
   {:db (assoc-in db [::replies-status post-id] :loading)
    :ajax/get {:url (str "/api/message/" post-id "/replies")
               :success-path [:replies]
               :success-event [::add-replies post-id]
               :error-event [::set-replies-error post-id]}}))

(rf/reg-event-db
 ::add-replies
 (fn [db [_ post-id replies]]
   (-> db
       (update ::posts (fnil into {}) (map (juxt :id identity)) replies)
       (assoc-in [::replies post-id] (map :id replies))
       (assoc-in [::replies-status post-id] :success))))

(rf/reg-event-db
 ::set-replies-error
 (fn [db [_ post-id response]]
   (-> db
       (assoc-in [::replies-status post-id] response))))

(rf/reg-event-db
 ::clear-replies
 (fn [db _]
   (dissoc db ::posts ::replies ::replies-status)))
;

;
(rf/reg-sub
 ::posts
 (fn [db _]
   (assoc
    (::posts db)
    (:id (::post db))
    (::post db))))

(rf/reg-sub
 ::reply
 :<- [::posts]
 (fn [posts [_ id]]
   (get posts id)))

(rf/reg-sub
 ::replies-map
 (fn [db _]
   (::replies db)))

(rf/reg-sub
 ::replies-for-post
 :<- [::replies-map]
 (fn [replies [_ id]]
   (get replies id)))

(rf/reg-sub
 ::replies-status-map
 (fn [db _]
   (::replies-status db)))

(rf/reg-sub
 ::replies-status
 :<- [::replies-status-map]
 (fn [statuses [_ id]]
   (get statuses id)))
;


;

(rf/reg-sub
 ::reply-count
 (fn [[_ id] _]
   (rf/subscribe [::reply id]))
 (fn [post _]
   (:reply_count post)))

(rf/reg-sub
 ::has-replies?
 (fn [[_ id] _]
   (rf/subscribe [::reply-count id]))
 (fn [c _]
   (not= c 0)))


(rf/reg-sub
 ::replies-to-load
 (fn [[_ id] _]
   [(rf/subscribe [::reply-count id]) (rf/subscribe [::replies-for-post id])])
 (fn [[c replies] _]
   (- c (count replies))))

(rf/reg-event-db
 ::expand-post
 (fn [db [_ id]]
   (update db ::expanded-posts (fnil conj #{}) id)))

(rf/reg-event-db
 ::collapse-post
 (fn [db [_ id]]
   (update db ::expanded-posts (fnil disj #{}) id)))

(rf/reg-event-db
 ::collapse-all
 (fn [db [_ id]]
   (dissoc db ::expanded-posts)))

(rf/reg-sub
 ::post-expanded?
 (fn [db [_ id]]
   (contains? (::expanded-posts db) id)))

;
(def post-controllers
  [{:parameters {:path [:post]}
    :start (fn [{{:keys [post]} :path}]
             (rf/dispatch [::fetch-post post])
             (rf/dispatch [::fetch-replies post]))
    :stop (fn [_]
            ;
            (rf/dispatch [::collapse-all])
            ;
            (rf/dispatch [::clear-post])
            (rf/dispatch [::clear-replies]))}])
;
;

(defn loading-bar []
  [:progress.progress.is-dark {:max 100} "30%"])

;
(defn reply [post-id]
  [msg/message @(rf/subscribe [::reply post-id]) {:include-link? false}])

(defn expand-control [post-id]
  (let [expanded? @(rf/subscribe [::post-expanded? post-id])
        reply-count @(rf/subscribe [::reply-count post-id])
        replies-to-load @(rf/subscribe [::replies-to-load post-id])
        loaded?         (= replies-to-load 0)
        status @(rf/subscribe [::replies-status post-id])]
    [:div.field.has-addons
     [:p.control>span.button.is-static  reply-count " replies"]
     [:p.control>button.button
      {:on-click (fn []
                   (if expanded?
                     (rf/dispatch [::collapse-post post-id])
                     
                     
                     (do
                       (when-not loaded?
                         (rf/dispatch [::fetch-replies post-id]))
                       (rf/dispatch [::expand-post post-id]))))
       :disabled (= status :loading)}
      (str (if expanded? "-" "+"))]
     (when expanded?
       [:p.control>button.button
        {:on-click #(rf/dispatch [::fetch-replies post-id])
         :disabled (= status :loading)}
        (if loaded?
          "↻"
          (str "Load " replies-to-load " New Replies"))])]))

(defn reply-tree [post-id]
  (when @(rf/subscribe [::has-replies? post-id])
    (let [status @(rf/subscribe [::replies-status post-id])]
      [:<>
       [expand-control post-id]
       (case status
         nil nil
         :success
         (when @(rf/subscribe [::post-expanded? post-id])
           [:div
            {:style {:border-left "1px dotted blue"
                     :padding-left "10px"}}
            (doall
             (for [id @(rf/subscribe [::replies-for-post post-id])]
               ^{:key id}
               [:<>
                [reply id]
                [reply-tree id]]))])

         :loading [loading-bar]
         ;; ELSE
         [:div
          [:h3 "Error"]
          [:pre (with-out-str (pprint status))]])])))
;
#_
;
(defn reply-tree [post-id]
  (let [reply-ids @(rf/subscribe [::replies-for-post post-id])
        status    @(rf/subscribe [::replies-status post-id])]
    (case status
      nil nil
      :success
      [:<>
       (doall
        (for [id reply-ids]
          (let [post @(rf/subscribe [::reply id])
                has-replies? (not= 0 (:reply_count post))]
            (if has-replies?
              ^{:key id}
              [:<>
               [msg/message post {:include-link? false}]
               [:button.button {:on-click #(rf/dispatch [::fetch-replies id])}
                "Load Replies"]
               [reply-tree id]]
              ^{:key id}
              [msg/message post {:include-link? false}]))))]

      :loading [loading-bar]
      ;; ELSE
      [:div
       [:h3 "Error"]
       [:pre (with-out-str (pprint status))]])))

(defn post [{:keys [name author message timestamp avatar id] 
             :as post-content}]
  [:div.content
   [:button.button.is-info.is-outlined.is-fullwidth
    {:on-click #(.back js/window.history)}
    "Back to Feed"]
   [:h3.title.is-3 "Post by " name
    "<" [:a {:href (str "/user/" author)} (str "@" author)] ">"]
   [:h4.subtitle.is-4 "Posted at " (.toLocaleString timestamp)]
   [msg/message post-content {:include-link? false}]
   [reply-tree id]])
;

(defn post-page [_]
  (let [post-content @(rf/subscribe [::post])
        {status :status
         {:keys [message]} :response
         :as error} @(rf/subscribe [::error])]
    (cond
      @(rf/subscribe [::loading?])
      [:div.content
       [:p "Loading Message..."]
       [loading-bar]]

      (seq error)
      (case status
        404
        [:div.content
         [:p (or message "Post not found.")]
         [:pre (with-out-str (pprint error))]]

        403
        [:div.content
         [:p (or message "You are not allowed to view this post.")]
         [:pre (with-out-str (pprint @error))]]

        [:div
         [:p (or message "Unknown Error")]
         [:pre (with-out-str (pprint @error))]])

      (seq post-content)
      [post post-content])))
;
