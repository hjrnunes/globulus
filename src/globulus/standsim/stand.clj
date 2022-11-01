(ns globulus.standsim.stand
  (:require [clojure.java.io :as io]
            [charred.api :as csv]))

(defn generate [{:keys [file
                        idstand                             ;; identifier of the stand
                        Area_ug                             ;; area of the management unit characterized by the stand
                        id_presc                            ;; identifier of the prescription
                        tlag                                ;; number of years to wait before the prescription will start being implemented
                        CoordX                              ;; xx rectangular coordinate of the stand centroid
                        CoordY                              ;; yy rectangular coordinate of the stand centroid
                        id_meteo                            ;; identifier of the meteorological station
                        Altitude                            ;; altitude of the stand (m)
                        year                                ;; year of measurement
                        month                               ;; month of measurement
                        Composition                         ;; stand composition: pure or mixed
                        plot_Type                           ;; Type of plot: Stand, gap, clump, burnt, harvested
                        Sp1                                 ;; dominant tree species: Ec= eucalyptus; Pb= maritime pine; Pm= sone pine; Sb= cork oak
                        Sp2                                 ;; dominated tree species: Ec= eucalyptus; Pb= maritime pine; Pm= sone pine; Sb= cork oak
                        structure                           ;; stand structure: J=uneven-aged; R=even-aged
                        S                                   ;; site index (m)
                        rot                                 ;; rotation: 1=planted; 2= 1st coppice; 3=2nd coppice, ...
                        t                                   ;; stand age (years)
                        tst                                 ;; number of years since the last thinning (only for Pm and Pb)
                        tsd                                 ;; number of years since the last debarking (only for Sb)
                        hdom                                ;; stand dominant height (m)
                        Nst                                 ;; Number of trees (ha-1), of stools for eucalyptus
                        N                                   ;; Number of trees (ha-1), of sprouts for eucalyptus
                        G                                   ;; Stand basal area (G2 ha-1)
                        Vu                                  ;; Volume with stump under bark (m3 ha-1)
                        Vb                                  ;; Volume of bark (m3 ha-1)
                        Vs                                  ;; Volume of stump (m2 ha-1)
                        Wr                                  ;; Root stand biomass (Mg ha-1)
                        Ww                                  ;; Wood stand biomass (Mg ha-1)
                        Wb                                  ;; Bark stand biomass (Mg ha-1)
                        Wbr                                 ;; Branches stand biomass (Mg ha-1)
                        Wl]                                 ;; Leaves stand biomass (Mg ha-1)
                 :or   {
                        ;idstand
                        Area_ug     1
                        ;id_presc
                        tlag        0
                        CoordX      0
                        CoordY      0
                        ;id_meteo
                        ;Altitude
                        ;year
                        month       0
                        Composition 0
                        plot_Type   "pov"
                        Sp1         "Ec"
                        Sp2         "Ec"
                        structure   "R"
                        ; S
                        rot         1
                        t           0
                        tst         0
                        tsd         0
                        hdom        0
                        Nst         0
                        N           0
                        G           0
                        Vu          0
                        Vb          0
                        Vs          0
                        Wr          0
                        Ww          0
                        Wb          0
                        Wbr         0
                        Wl          0}}]
  (let [header ["idstand"
                "Area_ug"
                "id_presc"
                "tlag"
                "CoordX"
                "CoordY"
                "id_meteo"
                "Altitude"
                "year"
                "month"
                "Composition"
                "plot_Type"
                "Sp1"
                "Sp2"
                "structure"
                "S"
                "rot"
                "t"
                "tst"
                "tsd"
                "hdom"
                "Nst"
                "N"
                "G"
                "Vu"
                "Vb"
                "Vs"
                "Wr"
                "Ww"
                "Wb"
                "Wbr"
                "Wl"]
        csvdata [header
                 [idstand
                  Area_ug
                  id_presc
                  tlag
                  CoordX
                  CoordY
                  id_meteo
                  Altitude
                  year
                  month
                  Composition
                  plot_Type
                  Sp1
                  Sp2
                  structure
                  S
                  rot
                  t
                  tst
                  tsd
                  hdom
                  Nst
                  N
                  G
                  Vu
                  Vb
                  Vs
                  Wr
                  Ww
                  Wb
                  Wbr
                  Wl]]]
    (if file
      (let [writer (io/writer file :encoding "windows-1252")]
        (csv/write-csv writer
                       csvdata
                       :newline :cr+lf))
      csvdata)))

(comment
  (generate {:idstand  1
             :id_presc "ID"
             :id_meteo 144
             :Altitude 14
             :year     2022
             :S        15
             :file     "/Users/hjrnunes/workspace/hjrnunes/globulus/resources/simdata/gen_cfg.csv"}))
