//======================================
//  Kyle Russell
//  AUT University 2016
//  Highly Secured Systems A2
//======================================

package com.kyleruss.hssa2.server.web.servlet;

import com.google.gson.JsonObject;
import com.kyleruss.hssa2.commons.CryptoCommons;
import com.kyleruss.hssa2.commons.CryptoUtils;
import com.kyleruss.hssa2.commons.EncryptedSession;
import com.kyleruss.hssa2.commons.RequestPaths;
import com.kyleruss.hssa2.server.entity.UserKeys;
import com.kyleruss.hssa2.server.entity.Users;
import com.kyleruss.hssa2.server.entityfac.UserKeysFacade;
import com.kyleruss.hssa2.server.entityfac.UsersFacade;
import com.kyleruss.hssa2.server.web.app.ServerKeyManager;
import com.kyleruss.hssa2.server.web.util.ServletUtils;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map.Entry;
import javax.crypto.Cipher;
import javax.ejb.EJB;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(name = "KeyServlet", urlPatterns = 
{
    RequestPaths.USER_PUBLIC_GET_REQ,
    RequestPaths.USER_PUBLIC_SEND_REQ,
    RequestPaths.SERV_PUBLIC_GET_REQ
}) 
public class KeyServlet extends HttpServlet
{
    @EJB
    private UsersFacade usersFacade; 
    
    @EJB
    private UserKeysFacade userKeysFacade; 
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException 
    {
       
        String path =   request.getServletPath();
        
        if(path.equals(RequestPaths.SERV_PUBLIC_GET_REQ))
            processServerPublicRequest(request, response); 
    }
    
    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     **/
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException 
    {
        String path =   request.getServletPath();
        
        switch(path)
        {
            case RequestPaths.USER_PUBLIC_GET_REQ:
                processUserPublicGetRequest(request, response);
                break;
            case RequestPaths.USER_PUBLIC_SEND_REQ:
                processUserPublicSendRequest(request, response);
                break;
            default: break;
        }
    }
    
    private void processServerPublicRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException
    {
        PublicKey publicKey     =   ServerKeyManager.getInstance().getServerPublicKey();
        JsonObject responseObj  =   new JsonObject();
        String encKey           =   Base64.getEncoder().encodeToString(publicKey.getEncoded());
        
        responseObj.addProperty("serverPublicKey", encKey);
        ServletUtils.jsonResponse(response, responseObj);
    }
    
    private void processUserPublicGetRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException
    {
        try
        {
            JsonObject inputObj                 =   ServletUtils.getPublicEncryptedClientJson(request, 
                                                    ServerKeyManager.getInstance().getServerPrivateKey());
            JsonObject responseObj              =   ServletUtils.createAuthResponseObjFromInput(inputObj);
            String requestedUserID              =   inputObj.get("reqUserID").getAsString();
            String userID                       =   inputObj.get("userID").getAsString();
            Users targetUser                    =   usersFacade.find(requestedUserID);
            Users user                          =   usersFacade.find(userID);
            
            if(targetUser != null && user != null)
            {
                String reqKeyStr        =   userKeysFacade.getKeyForUser(targetUser).getPubKey();
                String userKeyStr       =   userKeysFacade.getKeyForUser(user).getPubKey();
                PublicKey userPublicKey =   (PublicKey) CryptoUtils.stringToAsymKey(userKeyStr.getBytes("UTF-8"), true);

                responseObj.addProperty("reqPublicKeyresult", reqKeyStr);
                EncryptedSession encresponse    =   new EncryptedSession(responseObj.toString().getBytes("UTF-8"), userPublicKey);
                JsonObject encResponseObj       =   ServletUtils.prepareKeySessionResponse(encresponse);
                ServletUtils.jsonResponse(response, encResponseObj);
            }
        }
        
        catch(Exception e)
        {
            System.out.println(e.getMessage());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
   
    private void processUserPublicSendRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException
    {
        try
        {
            JsonObject passSaltObj              =   ServletUtils.getPublicEncryptedClientJson(request, 
                                                    ServerKeyManager.getInstance().getServerPrivateKey(), "authContents");
            
            byte[] password                     =   CryptoCommons.generateHash(passSaltObj.get("password").getAsString().getBytes("UTF-8"));
            byte[] salt                         =   CryptoCommons.generateHash(passSaltObj.get("salt").getAsString().getBytes("UTF-8"));
            byte[] data                         =   Base64.getDecoder().decode(request.getParameter("clientData").getBytes("UTF-8"));
            String decData                      =   CryptoCommons.pbeDecrypt(password, salt, data);
            JsonObject requestObj               =   ServletUtils.parseJsonInput(decData);
            JsonObject responseObj              =   ServletUtils.createAuthResponseObjFromInput(requestObj);
            System.out.println("Request obj: " + requestObj);
            
            String phoneID                      =   requestObj.get("phoneID").getAsString();
            String name                         =   requestObj.get("name").getAsString();
            String email                        =   requestObj.get("email").getAsString();
            String publicKey                    =   requestObj.get("publicKey").getAsString();
            byte[] publicKeyDecoded             =   Base64.getDecoder().decode(publicKey);
            
            Entry<Boolean, String> createUserResult     =   usersFacade.createUserAccount(phoneID, name, email);
            if(createUserResult.getKey())
            {
                Users user                              =   usersFacade.find(phoneID);
                Entry<Boolean, String> createKeyResult  =   userKeysFacade.createUserKey(user, publicKey);
                responseObj.addProperty("status", createKeyResult.getKey());
                responseObj.addProperty("statusMessage", createKeyResult.getValue());
            }
            
            else
            {
                responseObj.addProperty("status", false);
                responseObj.addProperty("statusMessage", createUserResult.getValue());
            }
            
            PublicKey userKey           =   (PublicKey) CryptoUtils.stringToAsymKey(publicKeyDecoded, true);
            byte[] responseBytes        =   responseObj.toString().getBytes("UTF-8");
            EncryptedSession encSession =   new EncryptedSession(responseBytes, userKey);  
            JsonObject wrappedObj       =   ServletUtils.prepareKeySessionResponse(encSession);
            ServletUtils.jsonResponse(response, wrappedObj);
        }
        
        catch(Exception e)
        {
            e.printStackTrace();
            System.out.println(e.getMessage());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
