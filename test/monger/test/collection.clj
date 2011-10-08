(set! *warn-on-reflection* true)

(ns monger.test.collection
  (:import  [com.mongodb WriteResult WriteConcern DBCursor DBObject CommandResult$CommandFailure]
            [org.bson.types ObjectId]
            [java.util Date])
  (:require [monger core result util conversion]
            [clojure stacktrace]
            [monger.collection :as mgcol])
  (:use [clojure.test]))

(monger.core/connect!)
(monger.core/set-db! (monger.core/get-db "monger-test"))


;;
;; fixture functions
;;

(defn purge-collection
  [collection-name, f]
  (mgcol/remove collection-name)
  (f)
  (mgcol/remove collection-name))

(defn purge-people-collection
  [f]
  (purge-collection "people" f))

(defn purge-docs-collection
  [f]
  (purge-collection "docs" f))

(defn purge-things-collection
  [f]
  (purge-collection "things" f))

(defn purge-libraries-collection
  [f]
  (purge-collection "libraries" f))

(use-fixtures :each purge-people-collection purge-docs-collection purge-things-collection purge-libraries-collection)


;;
;; insert
;;

(deftest insert-a-basic-document-without-id-and-with-default-write-concern
  (let [collection "people"
        doc        { :name "Joe", :age 30 }]
    (is (monger.result/ok? (mgcol/insert "people" doc)))
    (is (= 1 (mgcol/count collection)))))

(deftest insert-a-basic-document-without-id-and-with-explicit-write-concern
  (let [collection "people"
        doc        { :name "Joe", :age 30 }]
    (is (monger.result/ok? (mgcol/insert "people" doc WriteConcern/SAFE)))
    (is (= 1 (mgcol/count collection)))))

(deftest insert-a-basic-db-object-without-id-and-with-default-write-concern
  (let [collection "people"
        doc        (monger.conversion/to-db-object { :name "Joe", :age 30 })]
    (is (nil? (.get ^DBObject doc "_id")))
    (mgcol/insert "people" doc)
    (is (not (nil? (monger.util/get-id doc))))))



;;
;; insert-batch
;;

(deftest insert-a-batch-of-basic-documents-without-ids-and-with-default-write-concern
  (let [collection "people"
        docs       [{ :name "Joe", :age 30 }, { :name "Paul", :age 27 }]]
    (is (monger.result/ok? (mgcol/insert-batch "people" docs)))
    (is (= 2 (mgcol/count collection)))))

(deftest insert-a-batch-of-basic-documents-without-ids-and-with-explicit-write-concern
  (let [collection "people"
        docs       [{ :name "Joe", :age 30 }, { :name "Paul", :age 27 }]]
    (is (monger.result/ok? (mgcol/insert-batch "people" docs WriteConcern/NORMAL)))
    (is (= 2 (mgcol/count collection)))))




;;
;; count, remove
;;

(deftest get-collection-size
  (let [collection "things"]
    (is (= 0 (mgcol/count collection)))
    (mgcol/insert-batch collection [{ :language "Clojure", :name "langohr" }
                                                { :language "Clojure", :name "monger" }
                                                { :language "Clojure", :name "incanter" }
                                                { :language "Scala",   :name "akka" }] )
    (is (= 4 (mgcol/count collection)))
    (is (= 3 (mgcol/count collection { :language "Clojure" })))
    (is (= 1 (mgcol/count collection { :language "Scala"   })))
    (is (= 0 (mgcol/count collection { :language "Python"  })))))


(deftest remove-all-documents-from-collection
  (let [collection "libraries"]
    (mgcol/insert-batch collection [{ :language "Clojure", :name "monger" }
                                                { :language "Clojure", :name "langohr" }
                                                { :language "Clojure", :name "incanter" }
                                                { :language "Scala",   :name "akka" }])
    (is (= 4 (mgcol/count collection)))
    (mgcol/remove collection)
    (is (= 0 (mgcol/count collection)))))


(deftest remove-some-documents-from-collection
  (let [collection "libraries"]
    (mgcol/insert-batch collection [{ :language "Clojure", :name "monger" }
                                                { :language "Clojure", :name "langohr" }
                                                { :language "Clojure", :name "incanter" }
                                                { :language "Scala",   :name "akka" }])
    (is (= 4 (mgcol/count collection)))
    (mgcol/remove collection { :language "Clojure" })
    (is (= 1 (mgcol/count collection)))))



;;
;; find
;;

(deftest find-full-document-when-collection-is-empty
  (let [collection "docs"
        cursor     (mgcol/find collection)]
    (is (empty? (iterator-seq cursor)))))


;;
;; find-one
;;

(deftest find-one-full-document-when-collection-is-empty
  (let [collection "docs"]
    (is (nil? (mgcol/find-one collection {})))))

(deftest find-one-full-document-as-map-when-collection-is-empty
  (let [collection "docs"]
    (is (nil? (mgcol/find-one-as-map collection {})))))


(deftest find-one-full-document-when-collection-has-matches
  (let [collection "docs"
        doc-id     (monger.util/random-uuid)
        doc        { :data-store "MongoDB", :language "Clojure", :_id doc-id }]
    (mgcol/insert collection doc)
    (def ^DBObject found-one (mgcol/find-one collection { :language "Clojure" }))
    (is (= (:_id doc) (monger.util/get-id found-one)))
    (is (= (monger.conversion/from-db-object found-one true) doc))
    (is (= (monger.conversion/to-db-object doc) found-one))))


(deftest find-one-full-document-as-map-when-collection-has-matches
  (let [collection "docs"
        doc-id     (monger.util/random-uuid)
        doc        { :data-store "MongoDB", :language "Clojure", :_id doc-id }]
    (mgcol/insert collection doc)
    (is (= doc (mgcol/find-one-as-map collection { :language "Clojure" })))))



(deftest find-one-partial-document-when-collection-has-matches
  (let [collection "docs"
        doc-id     (monger.util/random-uuid)
        doc        { :data-store "MongoDB", :language "Clojure", :_id doc-id }
        fields     [:language]]
    (mgcol/insert collection doc)
    (def ^DBObject loaded (mgcol/find-one collection { :language "Clojure" } fields))
    (is (nil? (.get ^DBObject loaded "data-store")))
    (is (= doc-id (monger.util/get-id loaded)))
    (is (= "Clojure" (.get ^DBObject loaded "language")))))


(deftest find-one-partial-document-as-map-when-collection-has-matches
  (let [collection "docs"
        doc-id     (monger.util/random-uuid)
        doc        { :data-store "MongoDB", :language "Clojure", :_id doc-id }
        fields     [:data-store]]
    (mgcol/insert collection doc)
    (is (= { :data-store "MongoDB", :_id doc-id } (mgcol/find-one-as-map collection { :language "Clojure" } fields true)))))



;;
;; find-by-id
;;

(deftest find-full-document-by-string-id-when-that-document-does-not-exist
  (let [collection "libraries"
        doc-id     (monger.util/random-uuid)]
    (is (nil? (mgcol/find-by-id collection doc-id)))))

(deftest find-full-document-by-object-id-when-that-document-does-not-exist
  (let [collection "libraries"
        doc-id     (ObjectId.)]
    (is (nil? (mgcol/find-by-id collection doc-id)))))

(deftest find-full-document-by-id-as-map-when-that-document-does-not-exist
  (let [collection "libraries"
        doc-id     (monger.util/random-uuid)]
    (is (nil? (mgcol/find-map-by-id collection doc-id)))))


(deftest find-full-document-by-string-id-when-document-does-exist
  (let [collection "libraries"
        doc-id     (monger.util/random-uuid)
        doc        { :data-store "MongoDB", :language "Clojure", :_id doc-id }]
    (mgcol/insert collection doc)
    (is (= (doc (mgcol/find-by-id collection doc-id))))))

(deftest find-full-document-by-object-id-when-document-does-exist
  (let [collection "libraries"
        doc-id     (ObjectId.)
        doc        { :data-store "MongoDB", :language "Clojure", :_id doc-id }]
    (mgcol/insert collection doc)
    (is (= (doc (mgcol/find-by-id collection doc-id))))))

(deftest find-full-document-map-by-string-id-when-document-does-exist
  (let [collection "libraries"
        doc-id     (monger.util/random-uuid)
        doc        { :data-store "MongoDB", :language "Clojure", :_id doc-id }]
    (mgcol/insert collection doc)
    (is (= (doc (mgcol/find-map-by-id collection doc-id))))))

(deftest find-full-document-map-by-object-id-when-document-does-exist
  (let [collection "libraries"
        doc-id     (ObjectId.)
        doc        { :data-store "MongoDB", :language "Clojure", :_id doc-id }]
    (mgcol/insert collection doc)
    (is (= (doc (mgcol/find-map-by-id collection doc-id))))))

(deftest find-partial-document-by-id-when-document-does-exist
  (let [collection "libraries"
        doc-id     (monger.util/random-uuid)
        doc        { :data-store "MongoDB", :language "Clojure", :_id doc-id }]
    (mgcol/insert collection doc)
    (is (= ({ :language "Clojure" } (mgcol/find-by-id collection doc-id [ :language ]))))))


(deftest find-partial-document-as-map-by-id-when-document-does-exist
  (let [collection "libraries"
        doc-id     (monger.util/random-uuid)
        doc        { :data-store "MongoDB", :language "Clojure", :_id doc-id }]
    (mgcol/insert collection doc)
    (is (= ({ :language "Clojure" } (mgcol/find-map-by-id collection doc-id [ :language ]))))))


;;
;; find
;;

(deftest find-multiple-documents-when-collection-is-empty
  (let [collection "libraries"]
    (is (empty? (mgcol/find collection { :language "Scala" })))))

(deftest find-multiple-maps-when-collection-is-empty
  (let [collection "libraries"]
    (is (empty? (mgcol/find-maps collection { :language "Scala" })))))


(deftest find-multiple-documents
  (let [collection "libraries"]
    (mgcol/insert-batch collection [{ :language "Clojure", :name "monger" }
                                                { :language "Clojure", :name "langohr" }
                                                { :language "Clojure", :name "incanter" }
                                                { :language "Scala",   :name "akka" }])
    (is (= 1 (monger.core/count (mgcol/find collection { :language "Scala"   }))))
    (is (= 3 (.count (mgcol/find collection { :language "Clojure" }))))
    (is (empty? (mgcol/find collection      { :language "Java"    })))))

(deftest find-and-iterate-over-multiple-documents
  (let [collection "libraries"]
    (mgcol/insert-batch collection [{ :language "Clojure", :name "monger" }
                                                { :language "Clojure", :name "langohr" }
                                                { :language "Clojure", :name "incanter" }
                                                { :language "Scala",   :name "akka" }])
    (doseq [doc (take 3 (map (fn [dbo]
                               (monger.conversion/from-db-object dbo true))
                             (iterator-seq (mgcol/find collection { :language "Clojure" }))))]
      (is (= "Clojure" (:language doc))))))


(deftest find-multiple-maps
  (let [collection "libraries"]
    (mgcol/insert-batch collection [{ :language "Clojure", :name "monger" }
                                                { :language "Clojure", :name "langohr" }
                                                { :language "Clojure", :name "incanter" }
                                                { :language "Scala",   :name "akka" }])
    (is (= 1 (clojure.core/count (mgcol/find-maps collection { :language "Scala" }))))
    (is (= 3 (.count (mgcol/find-maps collection { :language "Clojure" }))))
    (is (empty? (mgcol/find-maps collection      { :language "Java"    })))))



(deftest find-multiple-partial-documents
  (let [collection "libraries"]
    (mgcol/insert-batch collection [{ :language "Clojure", :name "monger" }
                                                { :language "Clojure", :name "langohr" }
                                                { :language "Clojure", :name "incanter" }
                                                { :language "Scala",   :name "akka" }])
    (let [scala-libs   (mgcol/find collection { :language "Scala" } [:name])
          clojure-libs (mgcol/find collection { :language "Clojure"} [:language])]
      (is (= 1 (.count scala-libs)))
      (is (= 3 (.count clojure-libs)))
      (doseq [i clojure-libs]
        (let [doc (monger.conversion/from-db-object i true)]
          (is (= (:language doc) "Clojure"))))
      (is (empty? (mgcol/find collection { :language "Erlang" } [:name]))))))


;;
;; update, save
;;

(deftest update-document-by-id-without-upsert
  (let [collection "libraries"
        doc-id       (monger.util/random-uuid)
        date         (Date.)
        doc          { :created-at date, :data-store "MongoDB", :language "Clojure", :_id doc-id }
        modified-doc { :created-at date, :data-store "MongoDB", :language "Erlang",  :_id doc-id }]
    (mgcol/insert collection doc)
    (is (= (doc (mgcol/find-by-id collection doc-id))))
    (mgcol/update collection { :_id doc-id } { :language "Erlang" })
    (is (= (modified-doc (mgcol/find-by-id collection doc-id))))))


(deftest update-multiple-documents
  (let [collection "libraries"]
    (mgcol/insert collection { :language "Clojure", :name "monger" })
    (mgcol/insert collection { :language "Clojure", :name "langohr" })
    (mgcol/insert collection { :language "Clojure", :name "incanter" })
    (mgcol/insert collection { :language "Scala",   :name "akka" })
    (is (= 3 (mgcol/count collection { :language "Clojure" })))
    (is (= 1 (mgcol/count collection { :language "Scala"   })))
    (is (= 0 (mgcol/count collection { :language "Python"  })))
    (mgcol/update collection { :language "Clojure" } { "$set" { :language "Python" } } :multi true)
    (is (= 0 (mgcol/count collection { :language "Clojure" })))
    (is (= 1 (mgcol/count collection { :language "Scala"   })))
    (is (= 3 (mgcol/count collection { :language "Python"  })))))


(deftest save-a-new-document
  (let [collection "people"
        document       { :name "Joe", :age 30 }]
    (is (monger.result/ok? (mgcol/save "people" document)))
    (is (= 1 (mgcol/count collection)))))


(deftest save-a-new-basic-db-object
  (let [collection "people"
        doc        (monger.conversion/to-db-object { :name "Joe", :age 30 })]
    (is (nil? (monger.util/get-id doc)))
    (mgcol/save "people" doc)
    (is (not (nil? (monger.util/get-id doc))))))



(deftest update-an-existing-document-using-save
  (let [collection "people"
        doc-id            "people-1"
        document          { :_id doc-id, :name "Joe",   :age 30 }]
    (is (monger.result/ok? (mgcol/insert "people" document)))
    (is (= 1 (mgcol/count collection)))
    (mgcol/save collection { :_id doc-id, :name "Alan", :age 40 })
    (is (= 1 (mgcol/count collection { :name "Alan", :age 40 })))))


(deftest set-an-attribute-on-existing-document-using-update
  (let [collection "people"
        doc-id            (monger.util/object-id)
        document          { :_id doc-id, :name "Joe",   :age 30 }]
    (is (monger.result/ok? (mgcol/insert "people" document)))
    (is (= 1 (mgcol/count collection)))
    (is (= 0 (mgcol/count collection { :has_kids true })))
    (mgcol/update collection { :_id doc-id } { "$set" { :has_kids true } })
    (is (= 1 (mgcol/count collection { :has_kids true })))))



(deftest upsert-a-document
  (let [collection "libraries"
        doc-id       (monger.util/random-uuid)
        date         (Date.)
        doc          { :created-at date, :data-store "MongoDB", :language "Clojure", :_id doc-id }
        modified-doc { :created-at date, :data-store "MongoDB", :language "Erlang",  :_id doc-id }]
    (is (not (monger.result/updated-existing? (mgcol/update collection { :language "Clojure" } doc :upsert true))))
    (is (= 1 (mgcol/count collection)))
    (is (monger.result/updated-existing? (mgcol/update collection { :language "Clojure" } modified-doc :multi false :upsert true)))
    (is (= 1 (mgcol/count collection)))
    (is (= (modified-doc (mgcol/find-by-id collection doc-id))))
    (mgcol/remove collection)))


;;
;; indexes
;;

(deftest index-operations
  (let [collection "libraries"]
    (mgcol/drop-indexes collection)
    (is (= "_id_"
           (:name (first (mgcol/indexes-on collection)))))
    (is (nil? (second (mgcol/indexes-on collection))))
    (mgcol/create-index collection { "language" 1 })
    (is (= "language_1"
           (:name (second (mgcol/indexes-on collection)))))
    (mgcol/drop-index collection "language_1")
    (is (nil? (second (mgcol/indexes-on collection))))
    (mgcol/ensure-index collection { "language" 1 })
    (is (= "language_1"
           (:name (second (mgcol/indexes-on collection)))))
    (mgcol/ensure-index collection { "language" 1 })))


;;
;; exists?, drop, create
;;

(deftest checking-for-collection-existence-when-it-does-not-exist
  (let [collection "widgets"]
    (mgcol/drop collection)
    (is (false? (mgcol/exists? collection)))))

(deftest checking-for-collection-existence-when-it-does-exist
  (let [collection "widgets"]
    (mgcol/drop collection)
    (mgcol/insert-batch collection [{ :name "widget1" }
                                                { :name "widget2" }])
    (is (mgcol/exists? collection))
    (mgcol/drop collection)
    (is (false? (mgcol/exists? collection)))
    (mgcol/create "widgets" { :capped true :size 100000 :max 10 })
    (is (mgcol/exists? collection))
    (mgcol/rename collection "gadgets")
    (is (not (mgcol/exists? collection)))
    (is (mgcol/exists? "gadgets"))
    (mgcol/drop "gadgets")))
