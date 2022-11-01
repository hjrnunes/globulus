(ns globulus.standsim.fma
  (:require [charred.api :as csv]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [tablecloth.api :as tc]))

(defn parse-fma [file]
  (let [data (-> (slurp file)
                 (csv/read-csv))
        [r0 r1 r2 _r3 & _rows] data
        parsed {:id    (-> r0 first parse-long)
                :type  (-> r1 first parse-long)
                :years (-> r2 first parse-long)}
        op-rows (-> file
                    (tc/dataset {:n-initial-skip-rows 3})
                    (tc/rows :as-maps))
        ops (->> op-rows
                 (partition (:years parsed))
                 (map-indexed (fn [i ops]
                                (->> ops (map (fn [yops] (assoc yops :r i))))))
                 (mapcat identity)
                 (mapcat (fn [row]
                           (let [t (get row "T")
                                 r (:r row)]
                             (->> row
                                  (remove (comp #{"T" :r} key))
                                  (remove (comp zero? val))
                                  (map (fn [[k v]]
                                         {:t  t
                                          :r  r
                                          :op k
                                          :v  v})))))))]
    (assoc parsed :ops ops)))

(comment
  (let [file (-> (io/resource "simdata/FMA41_Ec_Regular.csv")
                 (str))]
    (parse-fma file)))

(def non-op-header
  ["T"                                                      ;; Stand age (years)
   "Npl"                                                    ;; Number of trees at planting (ha-1)
   "Mortality"                                              ;; Percentage of trees that die (%)
   "BeatUp"                                                 ;; Percentage of trees that died and will be replaced by new ones (0-100)
   "ShootSel"                                               ;; Average number of sprouts left per stool after the shoots selection
   "DensIncr"                                               ;; Stand density increase (trees ha-1)
   "StripIncr"                                              ;; Debarking height increase (m)
   "Prunn"                                                  ;; Percentage of trees to be pruned
   "Th_type"                                                ;; Thinning type (1 if Wilson Factor, 2 if Residual Basal Area)
   "ThGres"                                                 ;; Residual basal area value (m2 ha-1)
   "ThGrem"                                                 ;; Removed basal area value (m2 ha-1) (not implemented in this version)
   "ThFw"                                                   ;; Wilson factor value (varies from 0.1 up to 4)
   "ThCrCv%"])                                              ;; Crown cover percentage left after thinning (not implemented in this version)

(defn generate [{:keys [op-types data file]}]
  (let [op-header (->> op-types
                       (sort-by val)
                       (map key))
        t-row-header (concat non-op-header op-header)
        blank-row (zipmap t-row-header (repeat 0))
        {:keys [id type years ops]} data
        rotations (->> ops (map :r) (set) count)

        blanks (->> (range rotations)
                    (mapcat (fn [rotation]
                              (->> (range 1 (inc years))
                                   (map (fn [year] [[rotation year] (assoc blank-row "T" year)])))))
                    (into {}))

        pre-rows (-> (group-by (juxt :r :t) ops)
                     (update-vals (fn [t-ops]
                                    (->> t-ops
                                         (reduce (fn [r {:keys [t op v]}]
                                                   (assoc r "T" t op v))
                                                 blank-row)))))
        rows (->> (merge-with merge
                              blanks
                              pre-rows)
                  (sort-by (comp (juxt first second) key))
                  (map val))
        row-ds (-> (tc/dataset rows)
                   (tc/reorder-columns t-row-header))
        oprows (-> row-ds
                   (tc/rows :as-seqs))

        pad-row (fn [row total]
                  (let [padding (- total (count row))]
                    (concat row (repeat padding ""))))

        id-row (-> [id]
                   (pad-row (count t-row-header)))
        type-row (-> [type]
                     (pad-row (count t-row-header)))        ;;
        years-row (-> [years]
                      (pad-row (- (count t-row-header)
                                  (count op-header)))
                      (concat (->> op-types
                                   (sort-by val)
                                   (map val))))
        csvdata (concat [id-row                             ;; Forest Management Approach ID (4 for even-aged stands; 3 for uneven-aged stands)
                         type-row                           ;; Indicator of management (1 represents plantation management, 2 represents plantation followed by coppice management after the first harvest)
                         years-row                          ;; Maximum number of years for which the management operations will be defined (when the indicator of management is set to 2, the number of years defined will be applied both to the plantation and the coppice)
                         t-row-header
                         ]
                        oprows)]
    (if file
      (let [writer (io/writer file :encoding "windows-1252")]
        (csv/write-csv writer
                       csvdata
                       :newline :cr+lf))
      csvdata)
    )
  )


(comment
  (let [op-types (-> (io/resource "simdata/op_types.edn")
                     (str)
                     (slurp)
                     (edn/read-string))
        data (-> (io/resource "refs/FMA41_Ec_Regular.csv")
                 (str)
                 (parse-fma))
        file "/Users/hjrnunes/workspace/hjrnunes/globulus/resources/simdata/gen_fma.csv"]
    (generate {:op-types op-types
               :data     data
               :file     file})
    )
  )

(-> (io/resource "refs/FMA41_Ec_Regular.csv")
    (str)
    (parse-fma))