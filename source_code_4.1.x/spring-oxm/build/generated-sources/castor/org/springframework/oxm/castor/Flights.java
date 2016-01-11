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
public class Flights implements java.io.Serializable {

    private java.util.List<org.springframework.oxm.castor.Flight> flightList;

    public Flights() {
        super();
        this.flightList = new java.util.ArrayList<org.springframework.oxm.castor.Flight>();
    }

    /**
     * 
     * 
     * @param vFlight
     * @throws java.lang.IndexOutOfBoundsException if the index
     * given is outside the bounds of the collection
     */
    public void addFlight(final org.springframework.oxm.castor.Flight vFlight) throws java.lang.IndexOutOfBoundsException {
        this.flightList.add(vFlight);
    }

    /**
     * 
     * 
     * @param index
     * @param vFlight
     * @throws java.lang.IndexOutOfBoundsException if the index
     * given is outside the bounds of the collection
     */
    public void addFlight(final int index,final org.springframework.oxm.castor.Flight vFlight) throws java.lang.IndexOutOfBoundsException {
        this.flightList.add(index, vFlight);
    }

    /**
     * Method enumerateFlight.
     * 
     * @return an Enumeration over all possible elements of this
     * collection
     */
    public java.util.Enumeration<? extends org.springframework.oxm.castor.Flight> enumerateFlight() {
        return java.util.Collections.enumeration(this.flightList);
    }

    /**
     * Method getFlight.
     * 
     * @param index
     * @throws java.lang.IndexOutOfBoundsException if the index
     * given is outside the bounds of the collection
     * @return the value of the
     * org.springframework.oxm.castor.Flight at the given index
     */
    public org.springframework.oxm.castor.Flight getFlight(final int index) throws java.lang.IndexOutOfBoundsException {
        // check bounds for index
        if (index < 0 || index >= this.flightList.size()) {
            throw new IndexOutOfBoundsException("getFlight: Index value '" + index + "' not in range [0.." + (this.flightList.size() - 1) + "]");
        }

        return flightList.get(index);
    }

    /**
     * Method getFlight.Returns the contents of the collection in
     * an Array.  <p>Note:  Just in case the collection contents
     * are changing in another thread, we pass a 0-length Array of
     * the correct type into the API call.  This way we <i>know</i>
     * that the Array returned is of exactly the correct length.
     * 
     * @return this collection as an Array
     */
    public org.springframework.oxm.castor.Flight[] getFlight() {
        org.springframework.oxm.castor.Flight[] array = new org.springframework.oxm.castor.Flight[0];
        return this.flightList.toArray(array);
    }

    /**
     * Method getFlightCount.
     * 
     * @return the size of this collection
     */
    public int getFlightCount() {
        return this.flightList.size();
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
     * Method iterateFlight.
     * 
     * @return an Iterator over all possible elements in this
     * collection
     */
    public java.util.Iterator<? extends org.springframework.oxm.castor.Flight> iterateFlight() {
        return this.flightList.iterator();
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
     */
    public void removeAllFlight() {
        this.flightList.clear();
    }

    /**
     * Method removeFlight.
     * 
     * @param vFlight
     * @return true if the object was removed from the collection.
     */
    public boolean removeFlight(final org.springframework.oxm.castor.Flight vFlight) {
        boolean removed = flightList.remove(vFlight);
        return removed;
    }

    /**
     * Method removeFlightAt.
     * 
     * @param index
     * @return the element removed from the collection
     */
    public org.springframework.oxm.castor.Flight removeFlightAt(final int index) {
        java.lang.Object obj = this.flightList.remove(index);
        return (org.springframework.oxm.castor.Flight) obj;
    }

    /**
     * 
     * 
     * @param index
     * @param vFlight
     * @throws java.lang.IndexOutOfBoundsException if the index
     * given is outside the bounds of the collection
     */
    public void setFlight(final int index,final org.springframework.oxm.castor.Flight vFlight) throws java.lang.IndexOutOfBoundsException {
        // check bounds for index
        if (index < 0 || index >= this.flightList.size()) {
            throw new IndexOutOfBoundsException("setFlight: Index value '" + index + "' not in range [0.." + (this.flightList.size() - 1) + "]");
        }

        this.flightList.set(index, vFlight);
    }

    /**
     * 
     * 
     * @param vFlightArray
     */
    public void setFlight(final org.springframework.oxm.castor.Flight[] vFlightArray) {
        //-- copy array
        flightList.clear();

        for (int i = 0; i < vFlightArray.length; i++) {
                this.flightList.add(vFlightArray[i]);
        }
    }

    /**
     * Method unmarshal.
     * 
     * @param reader
     * @throws org.exolab.castor.xml.MarshalException if object is
     * null or if any SAXException is thrown during marshaling
     * @throws org.exolab.castor.xml.ValidationException if this
     * object is an invalid instance according to the schema
     * @return the unmarshaled org.springframework.oxm.castor.Flight
     */
    public static org.springframework.oxm.castor.Flights unmarshal(final java.io.Reader reader) throws org.exolab.castor.xml.MarshalException, org.exolab.castor.xml.ValidationException {
        return (org.springframework.oxm.castor.Flights) org.exolab.castor.xml.Unmarshaller.unmarshal(org.springframework.oxm.castor.Flights.class, reader);
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
