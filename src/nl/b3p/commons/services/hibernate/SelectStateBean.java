/*
 * $Id: SelectStateBean.java 769 2005-06-30 05:52:01Z Chris $
 */

package nl.b3p.commons.services.hibernate;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import nl.b3p.commons.services.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessages;
import org.apache.struts.util.MessageResources;
import org.apache.struts.validator.DynaValidatorForm;
import org.exolab.castor.xml.MarshalException;
import org.exolab.castor.xml.Marshaller;
import org.exolab.castor.xml.ValidationException;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;





/**
 * @author <a href="chrisvanlith@b3partners.nl">Chris van Lith</a>
 * @version $Revision: 769 $ $Date: 2005-06-30 07:52:01 +0200 (Thu, 30 Jun 2005) $
 */

public class SelectStateBean extends FormBaseBean {
    
    protected Log log = LogFactory.getLog(this.getClass());
    
    protected Session sess = null;
    
    protected String reportName = null;
    protected String xslField = null;
    protected String xsdField = null;
    protected String dateFrom = null;
    protected String dateTo = null;
    protected boolean doAnd = false;
    protected String preProcessTag = null;
    protected String postProcessTag = null;
    protected boolean changeStatus = false;
    
    protected String xslHttpPath = null;
    protected String xslFilePath = null;
    
    public static final String SEARCH_ACTION = "Search";
    public static final String SELECT_ACTION = "Select";
    public static final String REPORT_ACTION = "Report";
    public static final String SEARCH_BUTTON = "search";
    public static final String SELECT_BUTTON = "select";
    public static final String REPORT_BUTTON = "report";
    
    public static final int MAX_CRIT_LENGTH = 254;
    
    public static String defaultSearchField = "";
    
    protected ArrayList selectionList = null;
    protected StringBuffer searchDef = null;
    
    private StringWriter xmlString = null;
    
    public SelectStateBean(HttpServletRequest req,
            DynaValidatorForm dform,
            ActionMapping mapp) {
        this(req, null, null, null, dform, mapp);
    }
    
    public SelectStateBean(HttpServletRequest req,
            Locale loc,
            MessageResources mess,
            ActionMessages err,
            DynaValidatorForm dform,
            ActionMapping mapp) {
        super(req, loc, mess, err, dform, mapp);
        
        if (mapping==null || requestParams==null || session==null)
            return;
        
        try {
            setAction(getForm("action"));
            reportName = getForm("reportName");
            xslField = getForm("xsl");
            dateFrom = getForm("fromdate");
            dateTo = getForm("todate");
            if (getFormAsBoolean("doand")) {
                doAnd = true;
            }
            preProcessTag = getForm("pre");
            postProcessTag = getForm("post");
        } catch (B3pCommonsException me) {
            log.error("Init van SelectStateBean niet gelukt, oorzaak: ", me);
            isInit = false;
            return;
        }
        
        changeStatus = false;
        if (nullOrEmpty(getAction())) {
            changeStatus = true;
            setAction(SEARCH_ACTION);
        }
        if (buttonPressed(SEARCH_BUTTON)) {
            if (!isAction(SEARCH_ACTION)) {
                changeStatus = true;
            }
            setAction(SEARCH_ACTION);
        }
        if (buttonPressed(SELECT_BUTTON)) {
            if (!isAction(SELECT_ACTION)) {
                changeStatus = true;
            }
            setAction(SELECT_ACTION);
        }
        
        return;
    }
    
    protected ArrayList preProcess(ArrayList sl, String action, String ppt) {
        return sl;
    }
    
    protected ArrayList postProcess(ArrayList rl, String action, String ppt) {
        return rl;
    }
    
    public StringWriter transformRapport(Object rapport) throws B3pCommonsException {
        StringWriter xmlString = new StringWriter();
        
        //Checking xsl as file
        boolean xslFileAvailable = false;
        try {
            java.io.File file = new java.io.File(getXslFilePath() + xslField);
            if (file.exists())
                xslFileAvailable = true;
        } catch (Exception e) {
            log.error("xsl file is ongeldig: " + getXslFilePath() + xslField);
        }
        
        //Checking xsl as url
        boolean xslHttpAvailable = false;
        try {
            URL au = new URL(getXslHttpPath() + xslField);
            java.io.InputStream is = au.openStream();
            is.close();
            xslHttpAvailable = true;
        } catch (Exception e) {
            log.error("xsl url is ongeldig: " + getXslHttpPath() + xslField);
        }
        
        boolean transformDone = false;
        if (log.isDebugEnabled())
            log.debug("Starting Transformation block...");
        
        if (xslFileAvailable || xslHttpAvailable) {
            
            String xslFullPath = null;
            // Voer transformatie uit indien xsl gedefinieerd
            if (xslFileAvailable)
                xslFullPath = getXslFilePath() + xslField;
            else
                xslFullPath = getXslHttpPath() + xslField;
            log.debug(" Trying transformation with: " + xslFullPath);
            
            try {
                SAXTransformerFactory tFactory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
                log.debug("transformer used: " + tFactory.getClass().getName());
                TransformerHandler tHandler = tFactory.newTransformerHandler(new StreamSource(xslFullPath));
                tHandler.setResult(new StreamResult(xmlString));
                Marshaller marshal = new Marshaller(tHandler);
                marshal.marshal( rapport );
                transformDone = true;
            } catch (TransformerConfigurationException tce) {
                log.error("TransformerConfigurationException: " + tce);
            } catch (MarshalException me) {
                log.error("MarshalException: " + me);
            } catch (ValidationException ve) {
                log.error("ValidationException: " + ve);
            } catch (java.lang.OutOfMemoryError oome) {
                xmlString = null;
                log.error("OutOfMemoryError: " + oome);
            } catch (java.io.IOException ioe) {
                xmlString = null;
                log.error("IOException: " + ioe);
            }
            
            if (transformDone) {
                log.debug("   successful with: " + xslFullPath);
            } else {
                log.debug("   failed with: " + xslFullPath);
            }
        }
        
        if (!transformDone) {
            xmlString = new StringWriter();
            // anders voer xml uit
            log.debug(" Output XML without transformation");
            
            try {
                Marshaller marshal = new Marshaller(xmlString);
                if (xslHttpAvailable) {
                    String xslURL = getXslHttpPath() + xslField;
                    marshal.addProcessingInstruction("xml-stylesheet", "type='text/xsl' href='" + xslURL + "'");
                    log.debug(" Adding xsl url to xml for local transformation: " + xslURL);
                }
                if (xsdField!=null && xsdField.length()>0) {
                    String xsdURL = getXslHttpPath() + xsdField;
                    marshal.setNoNamespaceSchemaLocation(xsdURL);
                }
                marshal.setEncoding("ISO-8859-1");
                marshal.marshal( rapport );
                transformDone = true;
            } catch (MarshalException me) {
                log.error("MarshalException2: " + me);
            } catch (ValidationException ve) {
                log.error("ValidationException2: " + ve);
            } catch (java.lang.OutOfMemoryError oome) {
                xmlString = null;
                log.error("OutOfMemoryError2: " + oome);
            } catch (java.io.IOException ioe) {
                xmlString = null;
                log.error("IOException: " + ioe);
            }
            if (transformDone) {
                log.debug("   xml output successful");
            } else {
                log.debug("   xml output failed");
            }
        }
        
        log.debug("Finishing Transformation block...");
        
        if (!transformDone) {
            FileWriter writer = null;
            try {
                // Create a File to marshal to
                writer = new FileWriter(getXslFilePath() +"/error_output.xml");
                Marshaller marshal = new Marshaller(writer);
                marshal.marshal( rapport );
            } catch (Exception e) {
                log.debug("rapport xml kan niet weggeschreven worden! >> " + e.getMessage());
            } finally {
                if (writer!=null)
                    try {
                        writer.close();
                    } catch (IOException ioe) {}
            }
            throw new B3pCommonsException("Rapportgeneratie mislukt!");
        }
        
        if (xmlString != null && log.isDebugEnabled()) {
            log.debug("Output text: " + xmlString.toString().substring(0, 250) + "...");
        }
        
        return xmlString;
    }
    
    protected ActionForward prepareSelect() {
        // Creeer een hash met de zoektermen
        Hashtable tempSearchHash = new Hashtable();
        selectionList = new ArrayList();
        
        // Haal alle request parameters op en kijk of ze een Name/Value paar bevatten
        // dat als zoekterm gebruikt kan worden
        Set theParams = requestParams.keySet();
        Iterator it = theParams.iterator();
        while (it.hasNext()) {
            String theParameter = (String) it.next();
            String theValue = null;
            if (theParameter == null)
                continue;
            if (theParameter.equals("") ||
                    theParameter.equals("to") ||
                    theParameter.equals("page") ||
                    theParameter.equals("action") ||
                    theParameter.equals("xsl") ||
                    theParameter.equals("reportName") ||
                    theParameter.equals("fromdate") ||
                    theParameter.equals("todate") ||
                    theParameter.equals("doand") ||
                    theParameter.equals("org.apache.struts.taglib.html.TOKEN") ) {
                continue;
            }
            
            int namePos = theParameter.indexOf("Name");
            if ( (namePos)>=0) {
                String searchName = null;
                try {
                    searchName = getParamAsString(theParameter);
                } catch (B3pCommonsException be) {
                    log.error("Fout met type veldnaam, oorzaak: ", be);
                }
                if (searchName==null)
                    continue;
                // parameter bevat Name element van Name/Value paar
                String searchVar = "_";
                if (namePos>0)
                    searchVar = theParameter.substring(0,namePos);
                if (theParameter.length()>namePos+4)
                    searchVar += theParameter.substring(namePos+4);
                if (tempSearchHash.containsKey(searchVar)) {
                    // Er is eerder al een Value toegevoegd
                    Map scombi = (Map) tempSearchHash.get(searchVar);
                    Object v = scombi.values().toArray()[0];
                    scombi.clear();
                    scombi.put(searchName, v);
                    tempSearchHash.put(searchVar, scombi);
                } else {
                    Map scombi = new HashMap();
                    scombi.put(searchName, null);
                    tempSearchHash.put(searchVar, scombi);
                }
            }
            int valuePos = theParameter.indexOf("Value");
            if ( (valuePos)>=0) {
                Object searchValue = getParamAsObject(theParameter);
                if (searchValue==null)
                    continue;
                // parameter bevat Value element van Name/Value paar
                String searchVar = "_";
                if (valuePos>0)
                    searchVar = theParameter.substring(0,valuePos);
                if (theParameter.length()>valuePos+5)
                    searchVar += theParameter.substring(valuePos+5);
                if (tempSearchHash.containsKey(searchVar)) {
                    // Er is eerder al een Name toegevoegd
                    Map scombi = (Map) tempSearchHash.get(searchVar);
                    Object k = scombi.keySet().toArray()[0];
                    scombi.clear();
                    scombi.put(k, searchValue);
                    tempSearchHash.put(searchVar, scombi);
                } else {
                    // standaard wordt als Name genomen defaultSearchField, PRNr
                    Map scombi = new HashMap();
                    scombi.put(defaultSearchField, searchValue);
                    tempSearchHash.put(searchVar, scombi);
                }
            }
        }
        
        // Strip searchHash van onvolledige Name/Value paren
        searchDef = new StringBuffer();
        Enumeration senum = tempSearchHash.elements();
        while (senum.hasMoreElements()) {
            Map scombi = (Map) senum.nextElement();
            if (scombi!=null && scombi.size()>0) {
                Object k = scombi.keySet().toArray()[0];
                Object v = scombi.values().toArray()[0];
                if (k==null || v==null)
                    continue;
                // key moet altijd string zijn
                if (!(k instanceof String))
                    continue;
                // als key leeg is hoeft nergens op gezocht worden
                if (((String)k).trim().length()==0)
                    continue;
                // als value leeg is (en string is) hoeft nergens op gezocht worden
                if ((v instanceof String) && ((String)v).trim().length()==0)
                    continue;
                // als value string array is, maar er is geen enkel veld in gevuld
                // dan niet zoeken
                if (v instanceof String[]) {
                    String[] sa = (String[])v;
                    if (sa.length==0)
                        continue;
                    boolean leeg = true;
                    for (int i=0; i<sa.length; i++) {
                        if (sa[i]!=null && sa[i].trim().length()>0)
                            leeg = false;
                    }
                    if (leeg)
                        continue;
                }
                selectionList.add(scombi);
                
                // debug regel
                if (selectionList.size()>1)
                    searchDef.append(", ");
                searchDef.append(((String)k).toString());
                searchDef.append(" = '");
                searchDef.append(v.toString());
                searchDef.append("' ");
            }
        }
        if (log.isDebugEnabled())
            log.debug("Zoeken op: [" + searchDef.toString() + "].");
        
        return null;
    }
    
    protected String getSearchCriterium(Map scombi, String crit) {
        Object k = scombi.keySet().toArray()[0];
        if (k==null)
            return null;
        // key moet altijd string zijn
        if (!(k instanceof String))
            return null;
        String key = (String) k;
        // als key leeg is hoeft nergens op gezocht worden
        if (key.trim().length()==0)
            return null;
        if (!key.equals(crit))
            return null;
        
        Object v = scombi.values().toArray()[0];
        String retString = null;
        if (v instanceof String[]) {
            String[] sa = (String[])v;
            if (sa.length==0)
                return null;
            boolean leeg = true;
            StringBuffer cv = new StringBuffer();
            for (int i=0; i<sa.length; i++) {
                if (sa[i]!=null && sa[i].trim().length()>0) {
                    if (!leeg)
                        cv.append(", ");
                    cv.append(sa[i]);
                    leeg = false;
                }
            }
            if (leeg)
                return null;
            retString = cv.toString();
        } else if (v instanceof String)
            retString = (String)v;
        
        if (retString != null) {
            int pos = retString.length();
            if (pos>MAX_CRIT_LENGTH)
                pos = MAX_CRIT_LENGTH;
            return retString.substring(0, pos);
        }
        return null;
    }
    
    public ArrayList selectDirect(ArrayList recordsList, ArrayList searchList, SelectHelper sh)
    throws B3pCommonsException {
        // searchList bestaat uit OLV-beans met fieldname in Label en fieldvalue in Value.
        if (searchList == null)
            return null;
        
        List tempList = null;
        Query q = null;
        try {
            q = sh.queryBuilder(searchList, true);
            tempList = q.list();
        } catch (HibernateException he) {
            log.debug("Hibernate error, get list: ",  he);
            throw new B3pCommonsException("Query kan niet worden uitgevoerd", he);
        }
        
        // In deze list komen de gevonden items
        if (recordsList==null)
            recordsList = new ArrayList();
        if (tempList!=null && !tempList.isEmpty())
            recordsList.addAll(tempList);
        if (log.isDebugEnabled())
            log.debug("select records with query: " + q.toString() + ", found: " + recordsList.size());
        
        return recordsList;
    }
    
    public StringWriter getXmlString() {
        return xmlString;
    }
    
    public void setXmlString(StringWriter xmlString) {
        this.xmlString = xmlString;
    }
    
    public ActionForward process() throws B3pCommonsException {
        
        if (!isInit)
            return null;
        
        // Voorbereidende berekeningen
        ActionForward pForward = prepareSelect();
        if (pForward != null) {
            return pForward;
        }
        
        // Geef override functie preProcess de kans om de selectielijst te bewerken
        ArrayList preProcessedList = preProcess(selectionList, getAction(), preProcessTag);
        
        // Alleen zoeken indien geen changeStatus, alt:
        SelectHelper sh = new SelectHelper();
        if (!changeStatus && !selectionList.isEmpty()) {
            ArrayList pSelectResult = null;
            if (isAction(SEARCH_ACTION) || isAction(SELECT_ACTION)) {
                ArrayList resultList = selectDirect(null, preProcessedList, sh);
                pSelectResult = postProcess(resultList, getAction(), postProcessTag);
            } else if (isAction(REPORT_ACTION)) {
                ArrayList resultList = selectDirect(null, preProcessedList, sh);
                pSelectResult = postProcess(resultList, getAction(), postProcessTag);
                Object rapport = sh.addToReport(null, pSelectResult);
                
                xmlString = transformRapport(rapport);
                // return null om aan te geven dat direct naar response geschreven moet worden
                return null;
                
            }
            if (pSelectResult!=null) {
                //Zet het resultaat op de sessie
                session.setAttribute("pSelectResult", pSelectResult);
            } else {
                pSelectResult = new ArrayList();
                session.setAttribute("pSelectResult", pSelectResult);
            }
        } else {
            session.removeAttribute("pSelectResult");
        }
        
        setForm("action", getAction());
        if (isAction(SEARCH_ACTION) && changeStatus)
            setForm("fieldName",defaultSearchField);
        
        try {
            createLists();
        } catch (B3pCommonsException me) {
            return (mapping.findForward("failure"));
        }
        
        // Alles is OK
        pForward =  mapping.findForward("success");
        return pForward;
    }
    
    protected void createLists() throws B3pCommonsException {}
    
    public String getXslHttpPath() {
        return xslHttpPath;
    }
    
    public void setXslHttpPath(String xslHttpPath) {
        this.xslHttpPath = xslHttpPath;
    }
    
    public String getXslFilePath() {
        return xslFilePath;
    }
    
    public void setXslFilePath(String xslFilePath) {
        this.xslFilePath = xslFilePath;
    }
    
    
}
