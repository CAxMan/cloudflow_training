// Change the package name to your code
// The easiest way to do this is to do navigate in the project pane (to the left)
// syncServiceSkeleton -> Source Packages. Here you should see the current package name.
// Right click on it and choose refactor->rename.
package no.sintef.skeleton_syncservice;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jws.WebService;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.xml.ws.Holder;


// Change the serviceName. It will be part of the endpoint url to your service
@WebService(serviceName = "SyncExample")
public class SyncExample {

    
    // The namespace should match the package name in the first non-commented line of this file. 
    // If package name is a.b.c, the namespace should be "http://c.b.a/" (casae sensitive)
    // WFM will have an easier time recognizing your web service if this is fulfilled
    private final String namespace = "http://skeleton_syncservice.sintef.no/";
    
    // Also rename your Java project:
    // Right click on the project in the left pane, choose "Properties" and rename
    // group id, artifact id and name.
    
    // Access container configuration via environment variables
    private final static String config_value = System.getenv("MY_CONFIG_VALUE");
    
    
    // Name your service here, and add your required input and output parameters.
    // Note that output parameters should be of Holder objects.
    @WebMethod(operationName = "basicIO")
    public void basicIO(
            @WebParam(name = "serviceID", 
                    targetNamespace = namespace, 
                    mode = WebParam.Mode.IN) String serviceID,
            @WebParam(name = "sessionToken", 
                    targetNamespace = namespace, 
                    mode = WebParam.Mode.IN) String sessionToken,
            @WebParam(name = "input1",
                    targetNamespace = namespace,
                    mode = WebParam.Mode.IN) String input1,
            @WebParam(name = "input2",
                    targetNamespace = namespace,
                    mode = WebParam.Mode.IN) String input2,
            @WebParam(name = "myOut", 
                    targetNamespace = namespace, 
                    mode = WebParam.Mode.OUT) Holder<String> myOut)
    {
        // Edit the input and output parameters above to fit your need, and program the method body according to your wishes.
        
        // The value of the output parameters are sat like this:
        myOut.value = "Config was: " + config_value + "; input was: " + input1 
                + " " + input2;
    }
    
    // Now, only thing left is to modify the proxy filter.
    // Instructions starts on line 153 in the ProxyFilter class
    
    
    /*
    *  Utility function for less verbose logging
    */
    private void log(String message) {
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, message);
    }
    
    /*
    *  Utility function for less verbose error message in log
    */
    private void error(String message) {
        Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, message);
    }
    
    /*
    *  Utility function for less verbose error message in log
    */
    private void error(IOException ex) {
        Logger.getLogger(SyncExample.class.getName()).log(Level.SEVERE, null, ex);
    }
    
}
