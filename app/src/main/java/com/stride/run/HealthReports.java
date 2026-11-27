package com.stride.run;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Health reports (blood tests and other lab results) that you type in yourself, kept only on this phone. Each
 * report has a date and any number of tests {name, value, unit, low, high}; the range is the one printed on your
 * own report (labs differ), so nothing is judged against a range you didn't enter. The analysis shows how each test
 * moved between reports and sets it against what Kadamm measured in the 30 days up to each test.
 */
final class HealthReports {
    private HealthReports() {}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("kadamm_labs",Context.MODE_PRIVATE);}

    /** Tests by group, each {name, usual unit}. You can add any other test by name. */
    static final Object[][] GROUPS={
        {"Blood sugar",new String[][]{{"Fasting glucose","mg/dL"},{"Post-meal glucose (PP)","mg/dL"},{"Random glucose","mg/dL"},{"HbA1c","%"},{"Average blood glucose (eAG)","mg/dL"},{"Fasting insulin","µIU/mL"},{"HOMA-IR",""}}},
        {"Lipid profile",new String[][]{{"Total cholesterol","mg/dL"},{"LDL cholesterol","mg/dL"},{"HDL cholesterol","mg/dL"},{"Triglycerides","mg/dL"},{"VLDL cholesterol","mg/dL"},{"Non-HDL cholesterol","mg/dL"},{"Total cholesterol / HDL ratio",""},{"LDL / HDL ratio",""}}},
        {"Complete blood count",new String[][]{{"Haemoglobin","g/dL"},{"RBC count","million/µL"},{"Haematocrit (PCV)","%"},{"MCV","fL"},{"MCH","pg"},{"MCHC","g/dL"},{"RDW","%"},{"WBC count","/µL"},{"Neutrophils","%"},{"Lymphocytes","%"},{"Monocytes","%"},{"Eosinophils","%"},{"Basophils","%"},{"Platelet count","/µL"},{"MPV","fL"},{"ESR","mm/hr"}}},
        {"Liver function",new String[][]{{"SGOT (AST)","U/L"},{"SGPT (ALT)","U/L"},{"Alkaline phosphatase (ALP)","U/L"},{"GGT","U/L"},{"Total bilirubin","mg/dL"},{"Direct bilirubin","mg/dL"},{"Indirect bilirubin","mg/dL"},{"Total protein","g/dL"},{"Albumin","g/dL"},{"Globulin","g/dL"},{"A/G ratio",""}}},
        {"Kidney function",new String[][]{{"Creatinine","mg/dL"},{"Urea","mg/dL"},{"Blood urea nitrogen (BUN)","mg/dL"},{"BUN / creatinine ratio",""},{"Uric acid","mg/dL"},{"eGFR","mL/min/1.73m²"},{"Urine microalbumin","mg/L"}}},
        {"Thyroid",new String[][]{{"TSH","µIU/mL"},{"T3 (total)","ng/dL"},{"T4 (total)","µg/dL"},{"Free T3","pg/mL"},{"Free T4","ng/dL"},{"Anti-TPO","IU/mL"}}},
        {"Vitamins",new String[][]{{"Vitamin D (25-OH)","ng/mL"},{"Vitamin B12","pg/mL"},{"Folate (folic acid)","ng/mL"}}},
        {"Iron studies",new String[][]{{"Serum iron","µg/dL"},{"Ferritin","ng/mL"},{"TIBC","µg/dL"},{"Transferrin saturation","%"}}},
        {"Electrolytes and minerals",new String[][]{{"Sodium","mmol/L"},{"Potassium","mmol/L"},{"Chloride","mmol/L"},{"Bicarbonate","mmol/L"},{"Calcium","mg/dL"},{"Magnesium","mg/dL"},{"Phosphorus","mg/dL"}}},
        {"Inflammation",new String[][]{{"CRP","mg/L"},{"hs-CRP","mg/L"},{"Homocysteine","µmol/L"}}},
        {"Hormones",new String[][]{{"Testosterone (total)","ng/dL"},{"Cortisol (morning)","µg/dL"},{"Prolactin","ng/mL"},{"Estradiol","pg/mL"},{"LH","mIU/mL"},{"FSH","mIU/mL"},{"DHEA-S","µg/dL"}}},
        {"Other",new String[][]{{"PSA","ng/mL"},{"Creatine kinase (CK)","U/L"},{"Amylase","U/L"},{"Lipase","U/L"},{"Urine pH",""},{"Urine specific gravity",""}}},
    };
    static String groupOf(String name){for(Object[] g:GROUPS)for(String[] t:(String[][])g[1])if(t[0].equalsIgnoreCase(name))return (String)g[0];return "Other tests";}
    static String unitOf(String name){for(Object[] g:GROUPS)for(String[] t:(String[][])g[1])if(t[0].equalsIgnoreCase(name))return t[1];return "";}
    /** Position in the catalogue, so tests list in a familiar order; your own tests come last. */
    static int order(String name){int i=0;for(Object[] g:GROUPS)for(String[] t:(String[][])g[1]){if(t[0].equalsIgnoreCase(name))return i;i++;}return 10000;}

    // ---- Typical adult ranges ----
    /**
     * Typical adult reference ranges, used only when your report's own range wasn't entered, and always shown next
     * to it for comparison. {test, low, high, women's low, women's high} with NaN for an open end and the women's
     * pair NaN when it's the same for everyone. Sources: American Diabetes Association (glucose, HbA1c); NCEP ATP III
     * (lipids); Endocrine Society (vitamin D); AHA/CDC (hs-CRP); common clinical laboratory references for the rest.
     * Labs, ages and methods differ, which is why your report's range always comes first.
     */
    private static final double N=Double.NaN;
    private static final Object[][] TYPICAL={
        {"Fasting glucose",70d,99d,N,N},{"Post-meal glucose (PP)",N,139d,N,N},{"Random glucose",70d,139d,N,N},{"HbA1c",N,5.6,N,N},{"Average blood glucose (eAG)",N,116d,N,N},{"Fasting insulin",2d,25d,N,N},{"HOMA-IR",N,2.5,N,N},
        {"Total cholesterol",N,199d,N,N},{"LDL cholesterol",N,99d,N,N},{"HDL cholesterol",40d,N,50d,N},{"Triglycerides",N,149d,N,N},{"VLDL cholesterol",2d,30d,N,N},{"Non-HDL cholesterol",N,129d,N,N},{"Total cholesterol / HDL ratio",N,5d,N,N},{"LDL / HDL ratio",N,3.5,N,N},
        {"Haemoglobin",13.5,17.5,12d,15.5},{"RBC count",4.5,5.9,4.1,5.1},{"Haematocrit (PCV)",41d,53d,36d,46d},{"MCV",80d,100d,N,N},{"MCH",27d,33d,N,N},{"MCHC",32d,36d,N,N},{"RDW",11.5,14.5,N,N},
        {"WBC count",4000d,11000d,N,N},{"Neutrophils",40d,75d,N,N},{"Lymphocytes",20d,40d,N,N},{"Monocytes",2d,10d,N,N},{"Eosinophils",1d,6d,N,N},{"Basophils",0d,2d,N,N},{"Platelet count",150000d,450000d,N,N},{"MPV",7.5,11.5,N,N},{"ESR",0d,15d,0d,20d},
        {"SGOT (AST)",N,40d,N,32d},{"SGPT (ALT)",N,41d,N,33d},{"Alkaline phosphatase (ALP)",40d,130d,N,N},{"GGT",N,60d,N,40d},{"Total bilirubin",.3,1.2,N,N},{"Direct bilirubin",N,.3,N,N},{"Indirect bilirubin",.2,.8,N,N},
        {"Total protein",6d,8.3,N,N},{"Albumin",3.5,5d,N,N},{"Globulin",2d,3.5,N,N},{"A/G ratio",1d,2.2,N,N},
        {"Creatinine",.7,1.3,.6,1.1},{"Urea",15d,40d,N,N},{"Blood urea nitrogen (BUN)",7d,20d,N,N},{"BUN / creatinine ratio",10d,20d,N,N},{"Uric acid",3.4,7d,2.4,6d},{"eGFR",90d,N,N,N},{"Urine microalbumin",N,29d,N,N},
        {"TSH",.4,4.5,N,N},{"T3 (total)",80d,200d,N,N},{"T4 (total)",5d,12d,N,N},{"Free T3",2.3,4.2,N,N},{"Free T4",.8,1.8,N,N},{"Anti-TPO",N,34d,N,N},
        {"Vitamin D (25-OH)",30d,100d,N,N},{"Vitamin B12",200d,900d,N,N},{"Folate (folic acid)",3d,17d,N,N},
        {"Serum iron",65d,175d,50d,170d},{"Ferritin",24d,336d,11d,307d},{"TIBC",250d,450d,N,N},{"Transferrin saturation",20d,50d,N,N},
        {"Sodium",135d,145d,N,N},{"Potassium",3.5,5.1,N,N},{"Chloride",98d,107d,N,N},{"Bicarbonate",22d,29d,N,N},{"Calcium",8.6,10.3,N,N},{"Magnesium",1.7,2.2,N,N},{"Phosphorus",2.5,4.5,N,N},
        {"CRP",N,5d,N,N},{"hs-CRP",N,3d,N,N},{"Homocysteine",5d,15d,N,N},
        {"Testosterone (total)",264d,916d,15d,70d},{"Cortisol (morning)",6d,23d,N,N},{"Prolactin",2d,18d,2d,29d},
        {"PSA",N,4d,N,N},{"Creatine kinase (CK)",39d,308d,26d,192d},{"Urine pH",4.5,8d,N,N},{"Urine specific gravity",1.005,1.03,N,N},
    };
    /** {low, high} typical for you (women's values when Profile says female; both ends widened when it isn't set), or null. */
    static double[] typical(Context c,String name){
        for(Object[] t:TYPICAL){if(!((String)t[0]).equalsIgnoreCase(name))continue;double lo=(double)t[1],hi=(double)t[2],flo=(double)t[3],fhi=(double)t[4];
            String sex=c.getSharedPreferences("kadamm_settings",Context.MODE_PRIVATE).getString("sex","");
            if("f".equals(sex)){if(!Double.isNaN(flo)||!Double.isNaN(fhi))return new double[]{flo,fhi};return new double[]{lo,hi};}
            if("m".equals(sex)||Double.isNaN(flo)&&Double.isNaN(fhi))return new double[]{lo,hi};
            return new double[]{minNan(lo,flo),maxNan(hi,fhi)};}
        return null;
    }
    private static double minNan(double a,double b){return Double.isNaN(a)||Double.isNaN(b)?Double.NaN:Math.min(a,b);}
    private static double maxNan(double a,double b){return Double.isNaN(a)||Double.isNaN(b)?Double.NaN:Math.max(a,b);}
    static String rangeText(double lo,double hi){boolean l=!Double.isNaN(lo),h=!Double.isNaN(hi);return l&&h?fmt(lo)+"–"+fmt(hi):l?fmt(lo)+" or more":h?"up to "+fmt(hi):"";}

    // ---- Storage ----
    /** All reports, oldest first: {id, date yyyy-MM-dd, lab, tests:[{n, v, u, lo, hi}]} (lo/hi absent when not entered). */
    static List<JSONObject> all(Context c){
        List<JSONObject> out=new ArrayList<>();try{JSONArray a=new JSONArray(prefs(c).getString("reports","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)out.add(o);}}catch(Exception ignored){}
        out.sort((a,b)->a.optString("date").compareTo(b.optString("date")));return out;
    }
    static JSONObject get(Context c,long id){for(JSONObject o:all(c))if(o.optLong("id")==id)return o;return null;}
    static void save(Context c,JSONObject report){
        List<JSONObject> list=all(c);JSONArray a=new JSONArray();boolean replaced=false;
        for(JSONObject o:list){if(o.optLong("id")==report.optLong("id")){a.put(report);replaced=true;}else a.put(o);}
        if(!replaced)a.put(report);prefs(c).edit().putString("reports",a.toString()).apply();
    }
    static void delete(Context c,long id){JSONArray a=new JSONArray();for(JSONObject o:all(c))if(o.optLong("id")!=id)a.put(o);prefs(c).edit().putString("reports",a.toString()).apply();}

    // ---- Tests over time ----
    static final class Point { String date;double v,lo=Double.NaN,hi=Double.NaN,tlo=Double.NaN,thi=Double.NaN;String unit;boolean labRange; }
    static final class Marker {
        String name,unit;final List<Point> points=new ArrayList<>();
        Point last(){return points.get(points.size()-1);}
        Point prev(){return points.size()>=2?points.get(points.size()-2):null;}
        Point first(){return points.get(0);}
    }
    /** Every test across reports, in catalogue order, each with its values oldest first. */
    static List<Marker> markers(Context c,List<JSONObject> reports){List<Marker> out=markers(reports);
        // Your report's range first; the typical adult range fills in when you didn't enter one.
        for(Marker m:out){double[] t=typical(c,m.name);for(Point p:m.points){p.labRange=!Double.isNaN(p.lo)||!Double.isNaN(p.hi);if(t!=null){p.tlo=t[0];p.thi=t[1];if(!p.labRange){p.lo=t[0];p.hi=t[1];}}}}
        return out;
    }
    static List<Marker> markers(List<JSONObject> reports){
        Map<String,Marker> m=new LinkedHashMap<>();
        for(JSONObject r:reports){JSONArray t=r.optJSONArray("tests");for(int i=0;t!=null&&i<t.length();i++){JSONObject x=t.optJSONObject(i);if(x==null||!x.has("v"))continue;String n=x.optString("n").trim();if(n.isEmpty())continue;String k=n.toLowerCase(Locale.US);
            Marker mk=m.get(k);if(mk==null){mk=new Marker();mk.name=n;m.put(k,mk);}
            Point p=new Point();p.date=r.optString("date");p.v=x.optDouble("v");p.unit=x.optString("u","");if(x.has("lo"))p.lo=x.optDouble("lo");if(x.has("hi"))p.hi=x.optDouble("hi");mk.points.add(p);mk.unit=p.unit;}}
        List<Marker> out=new ArrayList<>(m.values());out.sort((a,b)->{int d=order(a.name)-order(b.name);return d!=0?d:a.name.compareToIgnoreCase(b.name);});return out;
    }
    /** -1 below the range you entered, 0 within it, 1 above it, 2 no range entered. */
    static int status(Point p){if(Double.isNaN(p.lo)&&Double.isNaN(p.hi))return 2;if(!Double.isNaN(p.lo)&&p.v<p.lo)return -1;if(!Double.isNaN(p.hi)&&p.v>p.hi)return 1;return 0;}
    static String statusWord(int s){return s==-1?"Below range":s==1?"Above range":s==0?"In range":"No range";}
    /** Moved toward the range (1), away from it (-1), or neither / no range (0), from the previous result. */
    static int direction(Point prev,Point now){
        if(prev==null)return 0;int s=status(now);if(s==2)return 0;
        double dPrev=distance(prev,now),dNow=distance(now,now);if(Math.abs(dPrev-dNow)<1e-9)return 0;return dNow<dPrev?1:-1;
    }
    /** How far a value is outside the range (using {@code ref}'s range), 0 inside. */
    private static double distance(Point p,Point ref){if(!Double.isNaN(ref.lo)&&p.v<ref.lo)return ref.lo-p.v;if(!Double.isNaN(ref.hi)&&p.v>ref.hi)return p.v-ref.hi;return 0;}
    static String fmt(double v){if(Math.abs(v-Math.rint(v))<1e-9&&Math.abs(v)<1e7)return String.format(Locale.US,"%,d",(long)Math.rint(v));return Math.abs(v)>=100?String.format(Locale.US,"%.1f",v):Math.abs(v)>=10?String.format(Locale.US,"%.1f",v):String.format(Locale.US,"%.2f",v).replaceAll("0+$","").replaceAll("\\.$","");}

    // ---- What Kadamm measured around each test ----
    /** The metrics set against tests: {key, name, unit}. */
    static final String[][] METRICS={{"steps","Steps","a day"},{"active","Session time","min a day"},{"floors","Floors","a day"},{"sleep","Sleep","h a night"},{"rhr","Resting heart rate","bpm"},{"hrv","HRV","ms"},
        {"resp","Breathing rate","/min"},{"spo2","Blood oxygen","%"},{"stress","Stress","of 100"},{"strain","Strain","of 21"},{"load","Movement load","of 21"},{"weight","Weight","kg"},{"bpSys","Blood pressure (top)","mmHg"},
        {"vo2","VO₂max","ml/kg/min"},{"fitness","Fitness","points"},{"efficiency","Aerobic efficiency","m/beat"}};
    static final int WINDOW=30;

    /**
     * Each metric's average over the {@link #WINDOW} days up to and including {@code day}: key → {average, days with
     * data}. Daily measures need 3+ days with data in the window; weight, VO₂max and blood pressure need 1+.
     */
    static Map<String,double[]> around(Context c,JSONArray hist,String day){return around(c,hist,day,WINDOW);}
    static Map<String,double[]> around(Context c,JSONArray hist,String day,int window){
        Map<String,double[]> out=new LinkedHashMap<>();JSONObject v=Vitals.all(c);
        List<String> days=new ArrayList<>();Calendar d=Calendar.getInstance();try{d.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(day));}catch(Exception e){return out;}
        for(int i=0;i<window;i++){days.add(DaySteps.key(d.getTimeInMillis()));d.add(Calendar.DAY_OF_YEAR,-1);}
        Map<String,Long> totals=DaySteps.allDayTotals(c);
        // Session minutes and efficiency per day from the history.
        Map<String,Double> active=new TreeMap<>();List<Double> eff=new ArrayList<>();
        for(int i=0;i<hist.length();i++){JSONObject o=hist.optJSONObject(i);if(o==null||!DaySummary.counts(o))continue;String k=o.optString("day","");if(!days.contains(k))continue;
            Double a=active.get(k);active.put(k,(a==null?0:a)+o.optLong("movingMs",o.optLong("durationMs"))/60000d);SessionMetrics m=SessionMetrics.of(c,o);if(m.efficiency>0)eff.add(m.efficiency);}
        TreeMap<String,Double> rhr=Pro.restSeries(c,v),hrv=Pro.hrvSeries(c,v);
        SessionMetrics.Fitness fit=SessionMetrics.fitness(c,hist);
        for(String[] m:METRICS){double sum=0;int n=0;String k=m[0];int need=Math.min(3,window);
            for(String dk:days){double x=Double.NaN;
                switch(k){
                    case "steps":{Long t=totals.get(dk);if(t!=null&&t>0)x=t;break;}
                    case "active":{if(totals.containsKey(dk)){Double a=active.get(dk);x=a==null?0:a;}break;}
                    case "floors":{double[] f=Pro.floors(c,hist,dk);if(f[0]>=0)x=f[0];break;}
                    case "sleep":{Double s=Vitals.series(v,"sleep").get(dk);if(s!=null&&s>=180)x=s/60;break;}
                    case "rhr":{Double s=rhr.get(dk);if(s!=null)x=s;break;}
                    case "hrv":{Double s=hrv.get(dk);if(s!=null)x=s;break;}
                    case "resp":case "spo2":case "weight":case "bpSys":case "vo2":{Double s=Vitals.series(v,k).get(dk);if(s!=null)x=s;if("weight".equals(k)&&Double.isNaN(x)){Double w=Trends.manualWeights(c).get(dk);if(w!=null)x=w;}need=1;break;}
                    case "stress":{JSONObject s=Pro.stress(c,dk);if(s!=null&&s.has("score"))x=s.optDouble("score");break;}
                    case "strain":{try{Pro.Strain s=Pro.strain(c,dk);if(s.strain>=0)x=s.strain;}catch(Exception ignored){}break;}
                    case "load":{try{Pro.Load l=Pro.load(c,hist,dk);if(l.load>=0)x=l.load;}catch(Exception ignored){}break;}
                    case "fitness":{double[] f=fit.days.get(dk);if(f!=null)x=f[0];need=1;break;}
                }
                if(!Double.isNaN(x)){sum+=x;n++;}
                if("fitness".equals(k)&&n>0)break;   // fitness is a running average already: the value on the test day
            }
            if("efficiency".equals(k)){if(eff.size()>=2){double s2=0;for(double e:eff)s2+=e;out.put(k,new double[]{s2/eff.size(),eff.size()});}continue;}
            if(n>=need)out.put(k,new double[]{sum/n,n});
        }
        return out;
    }
    /** Why a measure has no value around a test: what it needs. */
    static String missingReason(String key){
        switch(key){case "rhr":case "hrv":case "resp":case "spo2":case "stress":case "strain":return "needs a watch's readings";
            case "sleep":return "needs 3+ nights recorded";case "efficiency":return "needs sessions with heart rate";case "floors":return "none counted";
            case "bpSys":return "no reading";case "vo2":return "no VO₂max yet";case "weight":return "no weight logged";default:return "too few days of data";}
    }
    static String fmtMetric(String key,double v){
        switch(key){case "steps":return String.format(Locale.US,"%,d",Math.round(v));case "sleep":{long m=Math.round(v*60);return String.format(Locale.US,"%dh %02dm",m/60,m%60);}
            case "active":case "floors":case "rhr":case "hrv":case "stress":case "bpSys":return String.valueOf(Math.round(v));case "efficiency":return String.format(Locale.US,"%.2f",v);default:return String.format(Locale.US,"%.1f",v);}
    }
    static String metricName(String key){for(String[] m:METRICS)if(m[0].equals(key))return m[1];return key;}
    static String metricUnit(String key){for(String[] m:METRICS)if(m[0].equals(key))return m[2];return "";}

    // ---- Since your last report ----
    static final class Change { String metric;double before,now;int tone; }
    /** Days since a yyyy-MM-dd date. */
    static int daysSince(String day){try{long t=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(day).getTime();return (int)((System.currentTimeMillis()-t)/86400000L);}catch(Exception e){return -1;}}
    /**
     * Each measure in the 30 days up to your latest report against the days since it (up to 30, from 7 days on; the
     * report day and today, still going, left out), with whether the change is good (1), needs attention (-1) or neither (0). Big enough to
     * mention: steps, session time, floors, HRV, fitness and efficiency 10 %+, sleep 20+ minutes, resting heart rate
     * 3+ bpm, stress 8+ points, blood pressure 5+ mmHg, weight 1.5+ kg, blood oxygen 1+ point.
     */
    static List<Change> since(Context c,JSONArray hist){
        List<Change> out=new ArrayList<>();List<JSONObject> reps=all(c);if(reps.isEmpty())return out;String ld=reps.get(reps.size()-1).optString("date");int days=daysSince(ld);if(days<7)return out;
        // Today is still going, so the days since the report end yesterday.
        Map<String,double[]> before=around(c,hist,ld),now=around(c,hist,DaySteps.key(System.currentTimeMillis()-86400000L),Math.min(WINDOW,days-1));
        for(String[] m:METRICS){double[] a=before.get(m[0]),b=now.get(m[0]);if(a==null||b==null)continue;double d=b[0]-a[0],rel=a[0]!=0?d/Math.abs(a[0]):0;boolean big;int good;
            switch(m[0]){
                case "steps":case "active":case "floors":case "fitness":case "efficiency":case "load":big=Math.abs(rel)>=.10;good=d>0?1:-1;break;
                case "hrv":big=Math.abs(rel)>=.10;good=d>0?1:-1;break;
                case "vo2":big=Math.abs(d)>=1;good=d>0?1:-1;break;
                case "sleep":big=Math.abs(d)>=20/60d;good=b[0]>=9?0:d>0?1:-1;break;
                case "rhr":big=Math.abs(d)>=3;good=d<0?1:-1;break;
                case "stress":big=Math.abs(d)>=8;good=d<0?1:-1;break;
                case "bpSys":big=Math.abs(d)>=5;good=b[0]<=120&&a[0]<=120?0:d<0?1:-1;break;
                case "spo2":big=Math.abs(d)>=1;good=d>0?1:-1;break;
                case "weight":big=Math.abs(d)>=1.5;good=0;break;
                default:big=Math.abs(rel)>=.10;good=0;
            }
            if(!big)continue;Change ch=new Change();ch.metric=m[0];ch.before=a[0];ch.now=b[0];ch.tone=good;out.add(ch);}
        return out;
    }
    static String changeText(Change ch){
        String k=ch.metric;double d=ch.now-ch.before;
        String delta="sleep".equals(k)?String.format(Locale.US,"%+d min",Math.round(d*60)):"rhr".equals(k)||"bpSys".equals(k)?String.format(Locale.US,"%+d %s",Math.round(d),metricUnit(k)):"weight".equals(k)?String.format(Locale.US,"%+.1f kg",d):"stress".equals(k)||"spo2".equals(k)||"vo2".equals(k)?String.format(Locale.US,"%+.1f",d):String.format(Locale.US,"%+.0f %%",rel(ch.before,ch.now));
        return metricName(k)+" "+delta;
    }

    // ---- Tests against metrics ----
    static final class Link { String metric;double r;int n;double testFrom,testTo,metricFrom,metricTo; }
    /**
     * How a test moved with each metric across your reports. With 3+ reports where both exist: Pearson's correlation
     * between the test's values and the metric's 30-day averages (shown from |r| ≥ 0.5). With exactly 2: the change
     * in each, side by side. Correlation only says the two moved together in your results, not that one caused the other.
     */
    static List<Link> links(Marker mk,Map<String,Map<String,double[]>> aroundByDate){
        List<Link> out=new ArrayList<>();
        for(String[] m:METRICS){List<double[]> pairs=new ArrayList<>();
            for(Point p:mk.points){Map<String,double[]> a=aroundByDate.get(p.date);if(a==null)continue;double[] x=a.get(m[0]);if(x!=null)pairs.add(new double[]{p.v,x[0]});}
            if(pairs.size()<2)continue;Link l=new Link();l.metric=m[0];l.n=pairs.size();l.testFrom=pairs.get(0)[0];l.testTo=pairs.get(pairs.size()-1)[0];l.metricFrom=pairs.get(0)[1];l.metricTo=pairs.get(pairs.size()-1)[1];
            if(pairs.size()>=3){double r=pearson(pairs);if(Double.isNaN(r))continue;l.r=r;}else l.r=Double.NaN;
            out.add(l);}
        out.sort((a,b)->{boolean ca=!Double.isNaN(a.r),cb=!Double.isNaN(b.r);if(ca&&cb)return Double.compare(Math.abs(b.r),Math.abs(a.r));if(ca!=cb)return ca?-1:1;
            return Double.compare(Math.abs(rel(b.metricFrom,b.metricTo)),Math.abs(rel(a.metricFrom,a.metricTo)));});
        return out;
    }
    static double rel(double a,double b){return a!=0?(b-a)/Math.abs(a)*100:0;}
    static double pearson(List<double[]> p){
        int n=p.size();double mx=0,my=0;for(double[] q:p){mx+=q[0];my+=q[1];}mx/=n;my/=n;double sxy=0,sxx=0,syy=0;
        for(double[] q:p){sxy+=(q[0]-mx)*(q[1]-my);sxx+=(q[0]-mx)*(q[0]-mx);syy+=(q[1]-my)*(q[1]-my);}
        return sxx<=0||syy<=0?Double.NaN:sxy/Math.sqrt(sxx*syy);
    }
}
