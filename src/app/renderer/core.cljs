(ns app.renderer.core
  (:require [reagent.core :as r]
            [clojure.string :as string]
            ["electron" :refer [remote]]
            ["axios" :as axios]))

(enable-console-print!)

(declare app-state)
(def hca-client-token (subs global.location.search 1))

;;----------------------------------------------
;; Helper functions
;;----------------------------------------------

(defn render-template
  [template name]
  (string/replace template #"\{\{ Name \}\}" name))

(def fs (.require remote "fs"))
(def save-path "./saved-template.txt")
(defn save-template []
  (fs.writeFileSync save-path (:message-template @app-state))
  (swap! app-state assoc :show-saved-modal true))

(defn read-if-exists [path]
  (if (fs.existsSync path)
    (fs.readFileSync path "utf8")
    ""))

(defn restore-template []
  (swap! app-state assoc :message-template (read-if-exists save-path)))

(defn select-all-recipients []
  (let [all-selected (:all-selected @app-state)
        new-recipients (into {} (for [[k v] (:recipients @app-state)]
                                  [k (assoc v :selected (not all-selected))]))]
    (swap! app-state assoc :recipients new-recipients :all-selected (not all-selected))))

(defn toggle-recipient [chat-id]
  (swap! app-state update-in [:recipients chat-id :selected] not))

(defn- to-keyword [s]
  ({"chatId" :chat-id
    "patientId" :patient-id
    "name" :name} s))

(defn- clean-recipient [recipient]
  (into {} (for [[k v] recipient] [(to-keyword k) v])))

(defn- clean-recipients [recipients]
  (let [rs (map clean-recipient recipients)]
    (into {} (map (fn [r] [(:chat-id r) r]) rs))))

(defn retrieve-recipients []
  (-> (axios/get (str "http://prod.hcabot.workers.dev/listUsers/" hca-client-token "/"))
      (.then #(clean-recipients (get (js->clj %) "data")))
      (.then #(swap! app-state assoc :recipients %))))

(defn send-message-close-modal []
  (let [ids (->> (:recipients @app-state)
                 vals
                 (filter :selected)
                 (map :chat-id))
        message (:message-template @app-state)
        data {:message message :chatIds ids}]
    (-> (axios/post
         (str "http://prod.hcabot.workers.dev/sendMessage/" hca-client-token "/")
         (clj->js data))
        (.then #(swap! app-state assoc :show-confirm-modal false)))))

;;----------------------------------------------
;; app-state
;;----------------------------------------------

(comment
  "Shape of recipients"
  {"12321" {:name "Orange" :chat-id "12321" :patient-id "34125" :selected false}
   "12323" {:name "Pear" :chat-id "12323" :patient-id "53479" :selected false}}
  )

(def app-state
  (r/atom {:all-selected false
           :recipients {}
           :message-template ""
           :show-confirm-modal false
           :show-saved-modal false}))

;;----------------------------------------------
;; Components
;;----------------------------------------------

(defn name-number-table
  [recipients]
  [:table.table.is-bordered.is-narrow
   [:tbody
    [:tr [:th "Name"] [:th "Patient ID"]]
    (for [[k {:keys [name patient-id]}] recipients]
      ^{:key k} [:tr [:td name] [:td patient-id]])]])

(defn sample-message-display
  [message-template name]
  [:pre
   (if (and name message-template)
     (render-template message-template name)
     "No recipients selected")])

(defn confirm-modal [show message-template recipients]
  (let [name (:name (second (first (seq recipients))))]
    [:div.modal {:style {:display (if show :flex :none)}}
     [:div.modal-background {:on-click #(swap! app-state assoc :show-confirm-modal false)}]
     [:div.modal-card
      [:header.modal-card-head
       [:p.modal-card-title "Check before sending"]
       [:button.delete {:on-click #(swap! app-state assoc :show-confirm-modal false)}]]
      [:section.modal-card-body
       [:div.field
        [:label.label "Sample message"]
        [sample-message-display message-template name]]
       [:div.field
        [:label.label "The above will be sent to the following people"]
        [:div {:style {:padding-left "20px"}}
         (if (> (count recipients) 0)
           [name-number-table recipients]
           [:p "No recipients selected"])]]]
      [:footer.modal-card-foot
       [:button.button
        {:on-click #(swap! app-state assoc :show-confirm-modal false)}
        "Cancel"]
       [:button.button.is-link
        {:on-click send-message-close-modal}
        "Send"]]]]))

(defn saved-modal [show]
  [:div.modal {:style {:display (if show :flex :none)}}
   [:div.modal-background {:on-click #(swap! app-state assoc :show-saved-modal false)}]
   [:div.modal-content
    [:article.message
     [:div.message-header
      [:p "Saved message"]]
     [:div.message-body
      [:p "Message template was saved to " [:code save-path]]]]]])

(defn recipients-row [chat-id recipient]
  [:tr
   [:td [:input {:type "checkbox" :checked (boolean (:selected recipient)) :on-change #(toggle-recipient chat-id)}]]
   [:td (:name recipient)]
   [:td (:patient-id recipient)]])

(defn recipients-table [all-selected recipients]
  (if (> (count recipients) 0)
    [:table.table.is-narrow
     [:thead
      [:tr
       [:th [:input {:type "checkbox" :checked all-selected :on-change select-all-recipients}]]
       [:th "Name"]
       [:th "Patient ID"]]]
     [:tbody
      (for [[k v] recipients] ^{:key k} [recipients-row k v])]]
    [:div [:i "No recipients - retrieve them first?"]]))

(defn recipients-div [all-selected recipients]
  [:div
   [:div.field
    [:label.label "Recipients"]
    [:div [recipients-table all-selected recipients]]]
   [:div.buttons
    [:button.button
     {:on-click retrieve-recipients}
     "Retrieve"]]])

(defn root-component []
  [:div.container
   [confirm-modal
    (:show-confirm-modal @app-state)
    (:message-template @app-state)
    (filter (comp :selected second) (:recipients @app-state))]
   [saved-modal (:show-saved-modal @app-state)]
   [:div.columns
    [:div.column
     [recipients-div
      (:all-selected @app-state)
      (:recipients @app-state)]]]
   [:div.columns
    [:div.column
     [:div.field
      [:label.label "Message template"]
      [:div.control
       [:textarea.textarea {:placeholder "Copy and paste message template here"
                            :on-change #(swap! app-state assoc :message-template
                                               (-> % .-target .-value))
                            :value (:message-template @app-state)}]]]]]
   [:div.columns
    [:div.column
     [:div.buttons
      [:button.button
       {:on-click save-template}
       "Save template"]
      [:button.button
       {:on-click restore-template}
       "Restore template"]
      [:button.button.is-link
       {:on-click #(swap! app-state assoc :show-confirm-modal true)}
       "Send"]]]]])

(defn start! []
  (restore-template)
  (r/render
   [:section.section
    [root-component]]
   (js/document.getElementById "app-container")))
