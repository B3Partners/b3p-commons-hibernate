/*
 * $Id: SelectStateBean.java 769 2005-06-30 05:52:01Z Chris $
 */

package nl.b3p.commons.services.hibernate;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
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
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
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
import org.exolab.castor.xml.Marshaller;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;

import org.apache.avalon.framework.logger.Log4JLogger;
import org.apache.fop.messaging.MessageHandler;
import org.apache.log4j.Logger;
import org.exolab.castor.xml.MarshalException;
import org.exolab.castor.xml.ValidationException;
import org.apache.fop.apps.Driver;
import org.xml.sax.InputSource;




/**
 * @author <a href="chrisvanlith@b3partners.nl">Chris van Lith</a>
 * @version $Revision: 769 $ $Date: 2005-06-30 07:52:01 +0200 (Thu, 30 Jun 2005) $
 */

public class SelectStateBean extends FormBaseBean {
    
    protected static Log log = LogFactory.getLog(SelectStateBean.class);
    protected static Logger fopLogger = Logger.getLogger("fop");
    
    /*
     * rapport type constanten
     */
    public static final String HTML = "html";
    public static final String XSL = "xsl";
    public static final String PDF = "pdf";
    public static final String XML = "xml";
    public static final String NOT_IMPLEMENTED = "not implemented";            
    
    public static final String SEARCH_ACTION = "Search";
    public static final String SELECT_ACTION = "Select";
    public static final String REPORT_ACTION = "Report";
    public static final String SEARCH_BUTTON = "search";
    public static final String SELECT_BUTTON = "select";
    public static final String REPORT_BUTTON = "report";
    
    public static final int MAX_CRIT_LENGTH = 254;
    public static String defaultSearchField = "";
    
    private static Map contentTypes = new HashMap();
    static {
        contentTypes.put(HTML, "text/html");
        contentTypes.put(XSL, "text/xml");
        contentTypes.put(PDF, "application/pdf");
        contentTypes.put(XML, "text/xml");
    }
    
    protected Session sess = null;
    
    protected String reportName = null;
    protected String xslField = null;
    protected String type = HTML;
    protected String xsdField = null;
    protected String dateFrom = null;
    protected String dateTo = null;
    protected boolean doAnd = false;
    protected String preProcessTag = null;
    protected String postProcessTag = null;
    protected boolean changeStatus = false;
    
    protected String xslHttpPath = null;
    protected String xslFilePath = null;
    
    protected Map selectionList = null;
    protected StringBuffer searchDef = null;
    
    protected StringWriter xmlString = null;
    protected ByteArrayOutputStream byteArray = null;
    
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
    
    protected Map preProcess(Map sl, String action, String ppt) {
        return sl;
    }
    
    protected ArrayList postProcess(ArrayList rl, String action, String ppt) {
        return rl;
    }
    
    public void createXml(Object rapport) throws B3pCommonsException {
        byteArray = null;
        xmlString = new StringWriter();
        try {
            Marshaller marshal = new Marshaller(xmlString);
            marshal.setEncoding("ISO-8859-1");
            marshal.marshal( rapport );
        } catch (Exception ex) {
            throw new B3pCommonsException(ex);
        }
    }
    
    public void createHtml(Object rapport) throws B3pCommonsException {
        String xslFullPath = findXslFilePath();
        if (xslFullPath==null)
            return;
        
        byteArray = null;
        xmlString = new StringWriter();
        SAXTransformerFactory tFactory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
        log.debug("transformer used: " + tFactory.getClass().getName());
        try {
            TransformerHandler tHandler = tFactory.newTransformerHandler(new StreamSource(xslFullPath));
            tHandler.setResult(new StreamResult(xmlString));
            Marshaller marshal = new Marshaller(tHandler);
            marshal.marshal( rapport );
        } catch (Exception ex) {
            throw new B3pCommonsException(ex);
        }
    }
    
    public void createXsl(Object rapport) throws B3pCommonsException {
        String xslFullPath = findXslHttpPath();
        
        byteArray = null;
        xmlString = new StringWriter();
        try {
            Marshaller marshal = new Marshaller(xmlString);
            if (xslFullPath!=null) {
                String xslURL = xslFullPath + xslField;
                marshal.addProcessingInstruction("xml-stylesheet", "type='text/xsl' href='" + xslURL + "'");
                log.debug(" Adding xsl url to xml for local transformation: " + xslURL);
            }
            if (xsdField!=null && xsdField.length()>0) {
                String xsdURL = getXslHttpPath() + xsdField;
                marshal.setNoNamespaceSchemaLocation(xsdURL);
            }
            marshal.setEncoding("ISO-8859-1");
            marshal.marshal( rapport );
        } catch (Exception ex) {
            throw new B3pCommonsException(ex);
        }
    }
    
    public void createPdf(Object rapport) throws B3pCommonsException {
        String xslFullPath = findXslFilePath();
        if (xslFullPath==null)
            return;
        
        xmlString = null;
        byteArray = new ByteArrayOutputStream();
        try {
            
            File xslFile = new java.io.File(xslFullPath);
            File xmlPath = new java.io.File(xslFile.getParent());
            Source xslSource = new SAXSource(new InputSource(new FileInputStream(xslFile)));
                /* Zorg ervoor dat in de XSL met relatieve URL's bestanden kunnen worden
                 * geinclude
                 */
            xslSource.setSystemId(xmlPath.toURI().toString());
            
            /* Zorg ervoor dat fop relatieve URLs vanaf hier resolved... */
            org.apache.fop.configuration.Configuration.put("baseDir", xmlPath.getCanonicalPath());
            
            objectToPDF(rapport, xslSource, byteArray, true);
        } catch (Exception ex) {
            throw new B3pCommonsException(ex);
        }
    }
    
    /**
     * Transformeert een Object dat door Castor kan worden gemarshalled naar
     * een PDF met behulp van apache-fop.
     *
     * @param marshallable Het door Castor te marshallen object
     * @param xslFopSource Source van het XSL-FO document
     * @param out Output voor de gegenereerde PDF
     * @param validateMarshal Of Castor het marshallable object moet valideren of
     *          het voldoet aan het schema.
     */
    public static void objectToPDF(Object marshallable, Source xslFopSource, OutputStream out, boolean validateMarshal)
    throws TransformerConfigurationException, IOException, MarshalException, ValidationException {
        
        /* Deze methode maakt geen gebruik van een tijdelijk XML bestand maar
         * maakt gebruik van een SAXTransformerFactory die direct de SAX events
         * van Castor kan afhandelen en kan transformeren met de xslFopSource.
         * Het resultaat van de transformatie wordt met SAX events direct
         * doorgestuurd naar de ContentHandler van de Apache FOP Driver.
         */
        
        /* TODO check of optimalizatie van Marshalling zoals beschreven op
         * http://castor.org/xml-faq.html#How-can-I-speed-up-marshalling/unmarshalling-performance?
         * zin heeft OF dat Apache FOP zowiezo de meeste tijd inneemt.
         */
        
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        
        if(!transformerFactory.getFeature(SAXTransformerFactory.FEATURE)) {
            throw new UnsupportedOperationException("SAXTransformerFactory required");
        }
        
        SAXTransformerFactory saxTransformerFactory = (SAXTransformerFactory)transformerFactory;
        //log.debug("SAXTransformerFactory class: " + saxTransformerFactory.getClass());
        TransformerHandler transformer = saxTransformerFactory.newTransformerHandler(xslFopSource);
        
        Driver driver = new Driver();
        
        Log4JLogger logger = new Log4JLogger(fopLogger);
        driver.setLogger(logger);
        MessageHandler.setScreenLogger(logger);
        driver.setRenderer(Driver.RENDER_PDF);
        driver.setOutputStream(out);
        
        Result res = new SAXResult(driver.getContentHandler());
        transformer.setResult(res);
        
        Marshaller marshaller = new Marshaller(transformer);
        marshaller.setValidation(validateMarshal);
        marshaller.marshal(marshallable);
    }
    
    protected String findXslFilePath() throws B3pCommonsException {
        //Checking xsl as file
        boolean xslFileAvailable = false;
        try {
            java.io.File file = new java.io.File(xslFilePath + xslField);
            if (file.exists())
                xslFileAvailable = true;
        } catch (Exception e) {
            throw new B3pCommonsException(e);
        }
        if (!xslFileAvailable)
            throw new B3pCommonsException("xsl file is ongeldig: " + xslFilePath + xslField);
        return xslFilePath + xslField;
    }
    
    protected String findXslHttpPath() throws B3pCommonsException {
        //Checking xsl as file
        boolean xslHttpAvailable = false;
        try {
            java.io.File file = new java.io.File(xslHttpPath + xslField);
            if (file.exists())
                xslHttpAvailable = true;
        } catch (Exception e) {
            throw new B3pCommonsException(e);
        }
        if (!xslHttpAvailable)
            throw new B3pCommonsException("xsl file is ongeldig: " + xslHttpPath + xslField);
        return xslHttpPath + xslField;
    }
    
    protected ActionForward prepareSelect() {
        // Creeer een hash met de zoektermen
        Hashtable tempSearchHash = new Hashtable();
        selectionList = new HashMap();
        
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
                selectionList.put(k,v);
                
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
    
    public static ArrayList selectDirect(ArrayList recordsList, Map searchList, SelectHelper sh)
    throws B3pCommonsException {
        // searchList bestaat uit OLV-beans met fieldname in Label en fieldvalue in Value.
        if (searchList == null)
            return null;
        
        List tempList = null;
        Query q = null;
        try {
            q = sh.queryBuilder(searchList, true);
            if(log.isDebugEnabled()) {
                log.debug("about to execute query: " + q.toString());
            }
            tempList = q.list();
            if(log.isDebugEnabled()) {
                log.debug("results: " + tempList.size());
            }
        } catch (HibernateException he) {
            log.debug("Hibernate error, get list: ",  he);
            throw new B3pCommonsException("Query kan niet worden uitgevoerd", he);
        }
        
        // In deze list komen de gevonden items
        if (recordsList==null)
            recordsList = new ArrayList();
        if (tempList!=null && !tempList.isEmpty())
            recordsList.addAll(tempList);
        
        return recordsList;
    }
    
    public ActionForward process() throws Exception {
        
        if (!isInit)
            throw new B3pCommonsException("Niet geinitialiseerd!");
        
        // Voorbereidende berekeningen
        ActionForward pForward = prepareSelect();
        if (pForward != null) {
            return pForward;
        }
        
        // Geef override functie preProcess de kans om de selectielijst te bewerken
        Map preProcessedList = preProcess(selectionList, getAction(), preProcessTag);
        
        // Alleen zoeken indien geen changeStatus, alt:
        SelectHelper sh = new SelectHelper();
        if (!changeStatus && !selectionList.isEmpty()) {
            ArrayList pSelectResult = null;
            if (isAction(SEARCH_ACTION) || isAction(SELECT_ACTION)) {
                
                ArrayList resultList = selectDirect(null, preProcessedList, sh);
                pSelectResult = postProcess(resultList, getAction(), postProcessTag);
                
            } else if (isAction(REPORT_ACTION)) {
                
                if (response == null)
                    throw new B3pCommonsException("Response niet gedefinieerd!");
                
                ArrayList resultList = selectDirect(null, preProcessedList, sh);
                pSelectResult = postProcess(resultList, getAction(), postProcessTag);
                Object rapport = sh.addToReport(null, pSelectResult);
                
                if (HTML.equalsIgnoreCase(type)) {
                    createHtml(rapport);
                } else if (XSL.equalsIgnoreCase(type)) {
                    createXsl(rapport);
                } else if (PDF.equalsIgnoreCase(type)) {
                    createPdf(rapport);
                } else if (XML.equalsIgnoreCase(type)) {
                    createXml(rapport);
                } else {
                    throw new B3pCommonsException("Onbekend rapport type (xml, html, pdf zijn geldige waarden)! ");
                }
                
                response.setContentType(getContentType());
                if(xmlString != null) {
                    try {
                        Writer rw = response.getWriter();
                        rw.write(xmlString.toString());
                    } catch (IOException ex) {
                        throw new B3pCommonsException(ex);
                    }
                } else if (byteArray != null) {
                    try {
                        byte[] content = byteArray.toByteArray();
                        response.setContentLength(content.length);
                        response.getOutputStream().write(content);
                        response.getOutputStream().flush();
                    } catch (IOException ex) {
                        throw new B3pCommonsException(ex);
                    }
                    
                } else {
                    throw new B3pCommonsException("Rapport  is leeg! ");
                }
                
                // return null om aan te geven dat direct naar response is geschreven
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
    
    protected static String getSearchCriterium(Object v) {
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
    
    public String getContentType() {
        return (String) contentTypes.get(type);
    }
    
    public StringWriter getXmlString() {
        return xmlString;
    }
    
    public void setXmlString(StringWriter xmlString) {
        this.xmlString = xmlString;
    }
    
    public ByteArrayOutputStream getByteArray() {
        return byteArray;
    }
    
    public void setByteArray(ByteArrayOutputStream byteArray) {
        this.byteArray = byteArray;
    }
    
}
