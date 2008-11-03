/*
 * SelectHelper.java
 *
 * Created on 30 juni 2005, 10:00
 *
 */
package nl.b3p.commons.services.hibernate;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtilsBean;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.beanutils.PropertyUtilsBean;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;

/**
 *
 * @author Chris
 */
public class SelectHelper {

    protected static Log log = LogFactory.getLog("nl.b3p.commons.services.SelectHelper");
    protected Session sess = null;

    public SelectHelper() {
    }

    public Query queryBuilder(Map searchList, boolean andNotOr) throws HibernateException {
        return null;
    }

    public Object addToReport(Object report, ArrayList recordsList) {
        return report;
    }

    public static StringBuffer buildWhere(ArrayList whereSnippets, boolean andNotOr) {
        StringBuffer whereClause = new StringBuffer();
        Iterator it = whereSnippets.iterator();
        String snippet = null;
        boolean first = true;
        while (it.hasNext()) {
            snippet = (String) it.next();
            if (first) {
                whereClause.append(" where ");
            } else {
                if (andNotOr) {
                    whereClause.append(" and ");
                } else {
                    whereClause.append(" or ");
                }
            }
            whereClause.append("(").append(snippet).append(")");
            first = false;
        }
        return whereClause;
    }

    public static Query addParams(Query q, Hashtable hqlParams) throws HibernateException {
        if (q == null || hqlParams == null || hqlParams.isEmpty()) {
            return q;
        // Maak de query
        }
        Iterator it = hqlParams.keySet().iterator();
        String param = null;
        while (it.hasNext()) {
            param = (String) it.next();
            Object pv = hqlParams.get(param);
            if (pv instanceof Object[]) {
                q.setParameterList(param, (Object[]) pv);
            } else if (pv instanceof Collection) {
                q.setParameterList(param, (Collection) pv);
            } else {
                q.setParameter(param, pv);
            }
        }
        return q;
    }

    public static boolean copyHtoX(Object xo, Object ho) {
        if (ho == null || xo == null) {
            return false;
        }

        PropertyUtilsBean pub = new PropertyUtilsBean();
        // Bij probleem met conversie return null en geen exception gooien
        Converter bdConverter = new org.apache.commons.beanutils.converters.BigDecimalConverter(null);
        ConvertUtilsBean cub = new ConvertUtilsBean();
        cub.register(bdConverter, BigDecimal.class);
        BeanUtilsBean bub = new BeanUtilsBean(cub, pub);

        Map hoMap = null;
        try {
            hoMap = bub.describe(ho);
        } catch (Exception ex) {
            log.error("", ex);
        }
        if (hoMap == null || hoMap.isEmpty()) {
            return false;
        }
        Iterator it = hoMap.keySet().iterator();
        while (it.hasNext()) {
            String name = (String) it.next();
            try {
                Object val = bub.getSimpleProperty(ho, name);
                bub.setProperty(xo, name, val);
            } catch (NoSuchMethodException nsme) {
                log.debug("NoSuchMethodException: " + nsme.getMessage());
            } catch (IllegalAccessException iae) {
                log.debug("IllegalAccessException: " + iae.getMessage());
            } catch (IllegalArgumentException ige) {
                log.debug("IllegalArgumentException : " + ige.getMessage());
            } catch (InvocationTargetException ite) {
                log.debug("InvocationTargetException: " + ite.getMessage());
            } catch (ClassCastException cce) {
                log.debug("ClassCastException: " + cce.getMessage());
            } catch (ConversionException ce) {
                log.debug("ConversionException: " + ce.getMessage());
            }
        }
        return true;
    }
 }
