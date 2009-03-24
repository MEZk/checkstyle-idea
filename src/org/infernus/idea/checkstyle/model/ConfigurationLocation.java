package org.infernus.idea.checkstyle.model;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.infernus.idea.checkstyle.util.CheckStyleEntityResolver;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Bean encapsulating a configuration source.
 */
public abstract class ConfigurationLocation {

    private static final Log LOG = LogFactory.getLog(ConfigurationLocation.class);

    private ConfigurationType type;
    private String location;
    private String description;
    private Map<String, String> properties = new HashMap<String, String>();

    private boolean propertiesCheckedThisSession;

    /**
     * Create a new location.
     *
     * @param type        the type.
     * @param location    the location.
     * @param description an optional description of the file.
     */
    public ConfigurationLocation(final ConfigurationType type,
                                 final String location,
                                 final String description) {
        setType(type);
        setLocation(location);

        if (description == null) {
            this.description = location;
        } else {
            this.description = description;
        }
    }

    public ConfigurationType getType() {
        return type;
    }

    public void setType(final ConfigurationType type) {
        if (type == null) {
            throw new IllegalArgumentException("A type is required");
        }

        this.type = type;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(final String location) {
        if (location == null || location.trim().length() == 0) {
            throw new IllegalArgumentException("A non-blank location is required");
        }

        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        if (description == null) {
            this.description = location;
        } else {
            this.description = description;
        }
    }

    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    public void setProperties(final Map<String, String> newProperties) {
        properties.clear();

        if (newProperties == null) {
            return;
        }

        properties.putAll(newProperties);
    }

    /**
     * Extract all settable properties from the given configuration file.
     *
     * @param inputStream the configuration file.
     * @return the property names.
     */
    private List<String> extractProperties(final InputStream inputStream) {
        if (inputStream != null) {
            try {
                final SAXBuilder saxBuilder = new SAXBuilder();
                saxBuilder.setEntityResolver(new CheckStyleEntityResolver());
                final Document configDoc = saxBuilder.build(inputStream);
                return extractProperties(configDoc.getRootElement());

            } catch (Exception e) {
                LOG.error("CheckStyle file could not be parsed for properties.",
                        e);
            }
        }

        return new ArrayList<String>();
    }

    /**
     * Extract all settable properties from the given configuration element.
     *
     * @param element the configuration element.
     * @return the settable property names.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractProperties(final Element element) {
        final List<String> propertyNames = new ArrayList<String>();

        if (element != null) {
            if ("property".equals(element.getName())) {
                final String value
                        = element.getAttributeValue("value");
                // check is value is a token
                if (value != null && value.startsWith("${")
                        && value.endsWith("}")) {
                    final String propertyName = value.substring(2,
                            value.length() - 1);
                    propertyNames.add(propertyName);
                }
            }

            for (final Element child : (List<Element>) element.getChildren()) {
                propertyNames.addAll(extractProperties(child));
            }
        }

        return propertyNames;
    }

    /**
     * Resolve this location to a file.
     * <p/>
     *
     * @return the file to load.
     * @throws IOException if the file cannot be loaded.
     */
    public InputStream resolve() throws IOException {
        InputStream is = resolveFile();

        if (!propertiesCheckedThisSession) {
            // update property definitions
            final List<String> propertiesInFile = extractProperties(is);

            // merge properties from files
            for (final String propertyName : propertiesInFile) {
                if (!properties.containsKey(propertyName)) {
                    properties.put(propertyName, null);
                }
            }

            // remove redundant properties
            for (Iterator<String> i = properties.keySet().iterator();
                 i.hasNext();) {
                if (!propertiesInFile.contains(i.next())) {
                    i.remove();
                }
            }

            try {
                is.reset();
            } catch (IOException e) {
                is = resolveFile(); // JAR IS don't support this, for instance
            }

            propertiesCheckedThisSession = true;
        }

        return is;
    }

    /**
     * Resolve this location to a file.
     * <p/>
     *
     * @return the file to load.
     * @throws IOException if the file cannot be loaded.
     */
    protected abstract InputStream resolveFile() throws IOException;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof ConfigurationLocation)) {
            return false;
        }

        final ConfigurationLocation rhs = (ConfigurationLocation) obj;
        return new EqualsBuilder()
                .append(type, rhs.type)
                .append(location, rhs.location)
                .isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(type)
                .append(location)
                .toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        assert type != null;
        assert location != null;
        assert description != null;

        return type + ":" + location + ":" + description;
    }
}