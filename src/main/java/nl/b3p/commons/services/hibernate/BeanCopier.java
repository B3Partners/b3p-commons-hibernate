package nl.b3p.commons.services.hibernate;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.ConvertUtilsBean;
import org.apache.commons.beanutils.PropertyUtilsBean;

/**
 * Utility class to copy beans using BeanUtils. Use an instance of this object
 * to copy a large amount of objects efficiently. This class uses a single
 * BeanUtilsBean instance, so that the PropertyUtilsBean used by the
 * BeanUtilsBean can cache property descriptors.
 *
 * This class can also be configured to only copy scalar properties. This
 * can be useful for copying object trees where associations between objects
 * are copied manually; such as a conversion of a Hibernate object graph to a
 * Castor XML object graph, where you do not want to copy the entire object graph
 * (leading to the entire database being copied), or do not want to register
 * Convertors for all entity classes.
 *
 * Note that this class uses the "scalar" definition which includes primitive
 * wrapper classes and value object classes.
 *
 * NOTE: does not support multiple properties with the same name, but different
 * type!
 *
 * <b>Thread safety:</b>
 * Use a single instance only in one thread.
 *
 */
public class BeanCopier {

    /**
     * BeanUtilsBean used to copy properties. The PropertyUtilsBean will cache
     * property descriptors, so multiple calls to copyProperties() will not only
     * cache the type filtered property list but also the property descriptors
     * used by BeanUtilsBean to call the set method for the property.
     */
    private BeanUtilsBean beanUtilsBean = new BeanUtilsBean();

    private Set allowedPropertyTypes = new HashSet();

    /**
     * Once a class to copy is encountered for the first time, the properties
     * of that class are filtered according to the allowedPropertyTypes Set and
     * cached in this map. This map contains a List of property names of the
     * keyed Class, for which the types exist in the allowedPropertyTypes Set.
     *
     * When this cache is used the first time, the allowedPropertyTypes Set
     * will be made unmodifiable, so the cache will contain a list of filtered
     * properties which was filtered using an old allowedPropertyTypes Set.
     */
    private Map propertiesByClassCache = new HashMap();

    /**
     * Cache of properties in a dest bean which can be set for a orig bean whose
     * properties are filtered by the allowed types set.
     */
    private Map existingDestPropertiesByClassCache = new HashMap();

    /**
     * Create a BeanCopier that will only copy properties of the specified classes.
     * An empty Set will copy all properties, which would be equivalent to
     * BeanUtils.copyProperties().
     * @param allowedPropertyTypes Set of Class objects of property types to copy
     */
    public BeanCopier(Set allowedPropertyTypes) {
        this.allowedPropertyTypes.addAll(allowedPropertyTypes);

        /* Don't throw an exception on null BigDecimals and BigIntegers... */
        beanUtilsBean.getConvertUtils().register(
            new org.apache.commons.beanutils.converters.BigDecimalConverter(null), BigDecimal.class);
        beanUtilsBean.getConvertUtils().register(
            new org.apache.commons.beanutils.converters.BigIntegerConverter(null), BigInteger.class);
    }

    /**
     * Creates a BeanCopier that will only copy properties which are primitive
     * types, their wrapper classes and known value wrapper objects such as
     * Dates, BigInteger and BigDecimal classes.
     * 
     * @param scalarPropertiesOnly if only the described property types should
     *   be copied
     */
    public BeanCopier(boolean scalarPropertiesOnly) {
        this(new HashSet(Arrays.asList(new Class[] {
            java.lang.Boolean.class,
            java.lang.Boolean.TYPE,
            java.lang.Byte.class,
            java.lang.Byte.TYPE,
            java.lang.Character.class,
            java.lang.Character.TYPE,
            java.lang.Double.class,
            java.lang.Double.TYPE,
            java.lang.Float.class,
            java.lang.Float.TYPE,
            java.lang.Integer.class,
            java.lang.Integer.TYPE,
            java.lang.Long.class,
            java.lang.Long.TYPE,
            java.lang.Short.class,
            java.lang.Short.TYPE,
            java.lang.String.class,
            java.math.BigDecimal.class,
            java.math.BigInteger.class,
            java.util.Date.class,
            java.util.Calendar.class,
            java.sql.Date.class,
            java.sql.Time.class,
            java.sql.Timestamp.class
        })));
    }

    /**
     * Get the ConvertUtils instance of the BeanUtilsBean used to copy properties,
     * so you can register your own Converters.
     */
    public ConvertUtilsBean getBeanUtilsBeanConvertUtils() {
        return beanUtilsBean.getConvertUtils();
    }

    /**
     * Copy property values from the origin bean to the destination bean for
     * all cases where the property names are the same, only for properties of
     * the orig bean where the class exists in the allowedPropertyTypes Set.
     * Uses BeanUtils converters which can be configured with a ConvertUtilsBean./
     * @param dest
     * @param orig
     */
    public void copyProperties(Object dest, Object orig) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Set propertyNames = getTypeFilteredPropertyNamesExistingInDest(dest, orig);
        PropertyUtilsBean pub = beanUtilsBean.getPropertyUtils();
        for(Iterator it = propertyNames.iterator(); it.hasNext();) {
            String name = (String)it.next();
            Object value = pub.getProperty(orig, name);
            beanUtilsBean.copyProperty(dest, name, value);
        }
    }

    protected Set getTypeFilteredPropertyNamesExistingInDest(Object dest, Object orig) {
        Map destExistingMap = (Map)existingDestPropertiesByClassCache.get(orig.getClass());
        if(destExistingMap == null) {
            destExistingMap = new HashMap();
            existingDestPropertiesByClassCache.put(orig.getClass(), destExistingMap);
        }
        Set existingProperties = (Set)destExistingMap.get(dest.getClass());
        if(existingProperties == null) {
            existingProperties = new HashSet();
            Set origPropertyNames = getTypeFilteredPropertyNames(orig);
            /* Don't use filtering for dest properties, a converter may have been registered... */
            Set destPropertyNames = getBeanProperties(dest); 
            existingProperties.addAll(origPropertyNames);
            existingProperties.retainAll(destPropertyNames);
            destExistingMap.put(dest.getClass(), existingProperties);
        }
        return existingProperties;
    }

    protected Set getBeanProperties(Object bean) {
        HashSet names = new HashSet();
        PropertyDescriptor[] pds = beanUtilsBean.getPropertyUtils().getPropertyDescriptors(bean);
        for(int i = 0; i < pds.length; i++) {
            names.add(pds[i].getName());
        }
        return names;
    }
    
    protected Set getTypeFilteredPropertyNames(Object bean) {
        Set cachedPropertyNames = (Set)propertiesByClassCache.get(bean.getClass());
        if(cachedPropertyNames == null) {
            cachedPropertyNames = new HashSet();
            PropertyDescriptor[] pds = beanUtilsBean.getPropertyUtils().getPropertyDescriptors(bean);
            for(int i = 0; i < pds.length; i++) {
                if(allowedPropertyTypes.contains(pds[i].getPropertyType())) {
                    cachedPropertyNames.add(pds[i].getName());
                }
            }
            propertiesByClassCache.put(bean.getClass(), cachedPropertyNames);
        }
        return cachedPropertyNames;
    }
}
