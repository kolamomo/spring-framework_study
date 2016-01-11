/*
 * This class was automatically generated with 
 * <a href="http://www.castor.org">Castor 1.3.3</a>, using an XML
 * Schema.
 * $Id$
 */

package org.springframework.oxm.castor;

/**
 * 
 * 
 * @version $Revision$ $Date$
 */
@SuppressWarnings("serial")
public class OrderItemType implements java.io.Serializable {

    /**
     * Field id.
     */
    private java.lang.String id;

    /**
     * Field quantity.
     */
    private int quantity;

    /**
     * Keeps track of whether primitive field quantity has been set
     * already.
     */
    private boolean _hasquantity;

    public OrderItemType() {
        super();
    }

    /**
     */
    public void deleteQuantity() {
        this._hasquantity= false;
    }

    /**
     * Returns the value of field 'id'.
     * 
     * @return the value of field 'Id'.
     */
    public java.lang.String getId() {
        return this.id;
    }

    /**
     * Returns the value of field 'quantity'.
     * 
     * @return the value of field 'Quantity'.
     */
    public int getQuantity() {
        return this.quantity;
    }

    /**
     * Method hasQuantity.
     * 
     * @return true if at least one Quantity has been added
     */
    public boolean hasQuantity() {
        return this._hasquantity;
    }

    /**
     * Method isValid.
     * 
     * @return true if this object is valid according to the schema
     */
    public boolean isValid() {
        try {
            validate();
        } catch (org.exolab.castor.xml.ValidationException vex) {
            return false;
        }
        return true;
    }

    /**
     * 
     * 
     * @param out
     * @throws org.exolab.castor.xml.MarshalException if object is
     * null or if any SAXException is thrown during marshaling
     * @throws org.exolab.castor.xml.ValidationException if this
     * object is an invalid instance according to the schema
     */
    public void marshal(final java.io.Writer out) throws org.exolab.castor.xml.MarshalException, org.exolab.castor.xml.ValidationException {
        org.exolab.castor.xml.Marshaller.marshal(this, out);
    }

    /**
     * 
     * 
     * @param handler
     * @throws java.io.IOException if an IOException occurs during
     * marshaling
     * @throws org.exolab.castor.xml.ValidationException if this
     * object is an invalid instance according to the schema
     * @throws org.exolab.castor.xml.MarshalException if object is
     * null or if any SAXException is thrown during marshaling
     */
    public void marshal(final org.xml.sax.ContentHandler handler) throws java.io.IOException, org.exolab.castor.xml.MarshalException, org.exolab.castor.xml.ValidationException {
        org.exolab.castor.xml.Marshaller.marshal(this, handler);
    }

    /**
     * Sets the value of field 'id'.
     * 
     * @param id the value of field 'id'.
     */
    public void setId(final java.lang.String id) {
        this.id = id;
    }

    /**
     * Sets the value of field 'quantity'.
     * 
     * @param quantity the value of field 'quantity'.
     */
    public void setQuantity(final int quantity) {
        this.quantity = quantity;
        this._hasquantity = true;
    }

    /**
     * Method unmarshal.
     * 
     * @param reader
     * @throws org.exolab.castor.xml.MarshalException if object is
     * null or if any SAXException is thrown during marshaling
     * @throws org.exolab.castor.xml.ValidationException if this
     * object is an invalid instance according to the schema
     * @return the unmarshaled
     * org.springframework.oxm.castor.OrderItemType
     */
    public static org.springframework.oxm.castor.OrderItemType unmarshal(final java.io.Reader reader) throws org.exolab.castor.xml.MarshalException, org.exolab.castor.xml.ValidationException {
        return (org.springframework.oxm.castor.OrderItemType) org.exolab.castor.xml.Unmarshaller.unmarshal(org.springframework.oxm.castor.OrderItemType.class, reader);
    }

    /**
     * 
     * 
     * @throws org.exolab.castor.xml.ValidationException if this
     * object is an invalid instance according to the schema
     */
    public void validate() throws org.exolab.castor.xml.ValidationException {
        org.exolab.castor.xml.Validator validator = new org.exolab.castor.xml.Validator();
        validator.validate(this);
    }

}
