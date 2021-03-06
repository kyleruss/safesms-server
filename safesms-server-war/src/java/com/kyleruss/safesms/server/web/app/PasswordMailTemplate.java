//======================================
//  Kyle Russell
//  AUT University 2016
//  Highly Secured Systems A2
//======================================

package com.kyleruss.safesms.server.web.app;

public class PasswordMailTemplate 
{
    private static final String paramName   =   "{PASSWORD_PLACEHOLDER}";
    private static String template          = 
    "<html>"
    + "<body>"
        + "<h1>Hello,</h1>"
        + "<h3><small>Below is your verification password, please enter the code to finalize your registration<br>"
        + "This code will be valid for a limited time</small></h3>"
        + "<div style='border: 1px solid black; padding: 15px; width:100px'>" + paramName + "</div>"
    + "</body>"
    + "</html>";
    
    //Generates the password email template 
    //injects the password into it
    public static String getTemplate(String password)
    {
        String injectedTemplate =   template.replace(paramName, password);
        return injectedTemplate;
    }
}
