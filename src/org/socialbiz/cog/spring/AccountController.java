/*
 * Copyright 2013 Keith D Swenson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors Include: Shamim Quader, Sameer Pradhan, Kumar Raja, Jim Farris,
 * Sandia Yang, CY Chen, Rajiv Onat, Neal Wang, Dennis Tam, Shikha Srivastava,
 * Anamika Chaudhari, Ajay Kakkar, Rajeev Rastogi
 */

package org.socialbiz.cog.spring;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.socialbiz.cog.AuthRequest;
import org.socialbiz.cog.HistoricActions;
import org.socialbiz.cog.NGBook;
import org.socialbiz.cog.NGPageIndex;
import org.socialbiz.cog.NGWorkspace;
import org.socialbiz.cog.SiteReqFile;
import org.socialbiz.cog.SiteRequest;
import org.socialbiz.cog.UserManager;
import org.socialbiz.cog.exception.NGException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;
import org.workcast.json.JSONObject;

@Controller
public class AccountController extends BaseController {

    
    ////////////////////// MAIN VIEWS ///////////////////////
    
    @RequestMapping(value = "/{siteId}/$/SiteAdmin.htm", method = RequestMethod.GET)
    public void SiteAdmin(@PathVariable String siteId,
            HttpServletRequest request, HttpServletResponse response)throws Exception {
        AuthRequest ar = AuthRequest.getOrCreate(request, response);
        showJSPMembers(ar,siteId,null,"SiteAdmin");
    }
    
    
    @RequestMapping(value = "/{userKey}/requestAccount.htm", method = RequestMethod.GET)
    public ModelAndView requestSite(@PathVariable String userKey,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        try{
            AuthRequest ar = AuthRequest.getOrCreate(request, response);
            if(!ar.isLoggedIn()){
                return showWarningView(ar, "message.loginalert.see.page");
            }
            if (needsToSetName(ar)) {
                return new ModelAndView("requiredName");
            }
            if (UserManager.getAllSuperAdmins(ar).size()==0) {
                return showWarningView(ar, "nugen.missingSuperAdmin");
            }

            ModelAndView modelAndView = new ModelAndView("RequestAccount");
            request.setAttribute("userKey", userKey);
            request.setAttribute("pageTitle", "New Site Request Form");
            return modelAndView;
        }catch(Exception ex){
            throw new NGException("nugen.operation.fail.account.request.page", null, ex);
        }
    }

    @RequestMapping(value = "/{userKey}/accountRequests.form", method = RequestMethod.POST)
    public ModelAndView requestNewSite(@PathVariable
            String userKey, HttpServletRequest request, HttpServletResponse response)
            throws Exception {

        ModelAndView modelAndView = null;
        try{
            AuthRequest ar = AuthRequest.getOrCreate(request, response);
            if(!ar.isLoggedIn()){
                return showWarningView(ar, "message.loginalert.see.page");
            }

            String action = ar.reqParam( "action" );

            if(action.equals( "Submit" )){

                String accountID = ar.reqParam("accountID");
                String accountName = ar.reqParam("accountName");
                String accountDesc = ar.defParam("accountDesc","");

                HistoricActions ha = new HistoricActions(ar);
                ha.createNewSiteRequest(accountID, accountName, accountDesc);
            }
            else {
                throw new Exception("Method requestNewSite does not understand the action: "+action);
            }

            modelAndView = new ModelAndView(new RedirectView("userAccounts.htm"));
        }catch(Exception ex){
            throw new NGException("nugen.operation.fail.new.account.request", null , ex);
        }
        return modelAndView;
    }

    @RequestMapping(value = "/{userKey}/acceptOrDeny.form", method = RequestMethod.POST)
    public ModelAndView acceptOrDeny(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        try{
            AuthRequest ar = AuthRequest.getOrCreate(request, response);
            if(!ar.isLoggedIn()){
                return showWarningView(ar, "message.loginalert.see.page");
            }
            if(!ar.isSuperAdmin()){
                return showWarningView(ar, "message.superadmin.required");
            }

            String requestId = ar.reqParam("requestId");
            SiteRequest siteRequest = SiteReqFile.getRequestByKey(requestId);
            if (siteRequest==null) {
                throw new NGException("nugen.exceptionhandling.not.find.account.request",new Object[]{requestId});
            }

            String action = ar.reqParam("action");
            String description = ar.defParam("description", "");
            HistoricActions ha = new HistoricActions(ar);
            if ("Granted".equals(action)) {
                ha.completeSiteRequest(siteRequest, true, description);
            }
            else if("Denied".equals(action)) {
                ha.completeSiteRequest(siteRequest, false, description);
            }
            else{
                throw new Exception("Unrecognized action '"+action+"' in acceptOrDeny.form");
            }

            //TODO: need a go parameter
            return new ModelAndView(new RedirectView("requestedAccounts.htm"));
        }
        catch(Exception ex){
            throw new NGException("nugen.operation.fail.acceptOrDeny.account.request", null, ex);
        }
    }


    /**
    * This displays the page of site requests that have been made by others
    * and their current status.  Thus, only current executives and owners should see this.
    */
    @RequestMapping(value = "/{siteId}/$/roleRequest.htm", method = RequestMethod.GET)
    public ModelAndView remindersTab(@PathVariable String siteId,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        try{
            AuthRequest ar = AuthRequest.getOrCreate(request, response);
            if(!ar.isLoggedIn()){
                return showWarningView(ar, "message.loginalert.see.page");
            }
            NGBook site = prepareSiteView(ar, siteId);
            ModelAndView modelAndView = executiveCheckViews(ar);
            if (modelAndView != null) {
                return modelAndView;
            }

            modelAndView = new ModelAndView("account_role_request");
            request.setAttribute("realRequestURL", ar.getRequestURL());
            request.setAttribute("pageTitle", site.getFullName());
            return modelAndView;
        }catch(Exception ex){
            throw new NGException("nugen.operation.fail.account.role.request.page", new Object[]{siteId} , ex);
        }
    }


    @RequestMapping(value = "/{siteId}/$/accountListProjects.htm", method = RequestMethod.GET)
    public ModelAndView showSiteTaskTab(@PathVariable String siteId,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        AuthRequest ar = AuthRequest.getOrCreate(request, response);
        showJSPMembers(ar, siteId, null, "accountListProjects");
        return null;
    }

    @RequestMapping(value = "/{siteId}/$/accountCreateProject.htm", method = RequestMethod.GET)
    public ModelAndView accountCreateProject(@PathVariable String siteId,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        AuthRequest ar = AuthRequest.getOrCreate(request, response);
        showJSPMembers(ar, siteId, null, "accountCreateProject");
        return null;
    }
    
    @RequestMapping(value = "/{siteId}/$/accountCloneProject.htm", method = RequestMethod.GET)
    public ModelAndView accountCloneProject(@PathVariable String siteId,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        try{
            AuthRequest ar = AuthRequest.getOrCreate(request, response);
            if(!ar.isLoggedIn()){
                return showWarningView(ar, "message.loginalert.see.page");
            }
            NGBook site = prepareSiteView(ar, siteId);
            ModelAndView modelAndView = executiveCheckViews(ar);
            if (modelAndView != null) {
                return modelAndView;
            }

            request.setAttribute("realRequestURL", ar.getRequestURL());
            request.setAttribute("pageTitle", site.getFullName());
            return new ModelAndView("accountCloneProject");
        }catch(Exception ex){
            throw new NGException("nugen.operation.fail.account.process.page", new Object[]{siteId} , ex);
        }
    }

    @RequestMapping(value = "/{siteId}/$/convertFolderProject.htm", method = RequestMethod.GET)
    public ModelAndView convertFolderProject(@PathVariable String siteId,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        try{
            AuthRequest ar = AuthRequest.getOrCreate(request, response);
            if(!ar.isLoggedIn()){
                return showWarningView(ar, "message.loginalert.see.page");
            }
            prepareSiteView(ar, siteId);
            ModelAndView modelAndView = executiveCheckViews(ar);
            if (modelAndView != null) {
                return modelAndView;
            }

            return new ModelAndView("convertFolderProject");
        }catch(Exception ex){
            throw new NGException("nugen.operation.fail.account.process.page",
                    new Object[]{siteId} , ex);
        }
    }


    @RequestMapping(value = "/{siteId}/$/SiteUsers.htm", method = RequestMethod.GET)
    public ModelAndView SiteUsers(@PathVariable String siteId,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        try{
            AuthRequest ar = AuthRequest.getOrCreate(request, response);
            if(!ar.isLoggedIn()){
                return showWarningView(ar, "message.loginalert.see.page");
            }
            prepareSiteView(ar, siteId);
            ModelAndView modelAndView = executiveCheckViews(ar);
            if (modelAndView != null) {
                return modelAndView;
            }

            modelAndView = new ModelAndView("SiteUsers");
            return modelAndView;
        }catch(Exception ex){
            throw new Exception("Unable to handle SiteUsers.htm for site '"+siteId+"'", ex);
        }
    }


    @RequestMapping(value = "/{siteId}/$/account_settings.htm", method = RequestMethod.GET)
    public ModelAndView showProjectSettingsTab(@PathVariable String siteId,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        ModelAndView modelAndView = new ModelAndView(new RedirectView("personal.htm"));
        return modelAndView;
    }


    @RequestMapping(value = "/approveAccountThroughMail.htm", method = RequestMethod.GET)
    public ModelAndView approveSiteThroughEmail(
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        ModelAndView modelAndView = null;
        try{
            AuthRequest ar = AuthRequest.getOrCreate(request, response);

            String requestId = ar.reqParam("requestId");
            if(!ar.isLoggedIn()){
                return showWarningView(ar, "message.loginalert.see.page");
            }
            if(!ar.isSuperAdmin()){
                return showWarningView(ar, "message.superadmin.required");
            }

            //Note: the approval page works in two modes.
            //1. if you are super admin, you have buttons to grant or deny
            //2. if you are not super admin, you can see status, but can not change status

            modelAndView = new ModelAndView("approveAccountThroughMail");
            modelAndView.addObject("requestId", requestId);
        }catch(Exception ex){
            throw new NGException("nugen.operation.fail.account.approve.through.mail", null, ex);
        }
        return modelAndView;
    }

    @RequestMapping(value = "/{siteId}/$/CreateAccountRole.form", method = RequestMethod.POST)
    public ModelAndView createRole(@PathVariable String siteId,HttpServletRequest request,
            HttpServletResponse response)
    throws Exception {
        try {
            AuthRequest ar = AuthRequest.getOrCreate(request, response);
            if(!ar.isLoggedIn()){
                return showWarningView(ar, "message.loginalert.see.page");
            }
            NGBook site = prepareSiteView(ar, siteId);
            ModelAndView modelAndView = executiveCheckViews(ar);
            if (modelAndView != null) {
                return modelAndView;
            }

            String roleName=ar.reqParam("rolename");
            String des=ar.reqParam("description");

            site.createRole(roleName,des);
            site.saveFile(ar, "Add New Role "+roleName+" to roleList");

            return new ModelAndView(new RedirectView("permission.htm"));
        } catch (Exception e) {
            throw new NGException("nugen.operation.fail.account.create.role",new Object[]{siteId}, e);
        }
    }





    @RequestMapping(value = "/{siteId}/$/public.htm", method = RequestMethod.GET)
    public ModelAndView sitePublic(@PathVariable String siteId,@PathVariable String pageId,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        AuthRequest ar = AuthRequest.getOrCreate(request, response);
        return redirectBrowser(ar, "accountListProjects.htm");
    }
    @RequestMapping(value = "/{siteId}/$/member.htm", method = RequestMethod.GET)
    public ModelAndView member(@PathVariable String siteId,@PathVariable String pageId,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        AuthRequest ar = AuthRequest.getOrCreate(request, response);
        return redirectBrowser(ar, "accountListProjects.htm");
    }




    @RequestMapping(value = "/{siteId}/$/permission.htm", method = RequestMethod.GET)
    public ModelAndView showPermissionTab(@PathVariable String siteId,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        try{
            AuthRequest ar = AuthRequest.getOrCreate(request, response);
            if(!ar.isLoggedIn()){
                return showWarningView(ar, "message.loginalert.see.page");
            }
            NGBook site = prepareSiteView(ar, siteId);
            ModelAndView modelAndView = executiveCheckViews(ar);
            if (modelAndView != null) {
                return modelAndView;
            }
            modelAndView = new ModelAndView("account_permission");
            request.setAttribute("headerType", "site");
            request.setAttribute("realRequestURL", ar.getRequestURL());
            request.setAttribute("pageTitle", site.getFullName());
            return modelAndView;
        }catch(Exception ex){
            throw new NGException("nugen.operation.fail.account.permission.page", new Object[]{siteId}, ex);
        }
    }


    @RequestMapping(value = "/{siteId}/$/personal.htm", method = RequestMethod.GET)
    public ModelAndView showPersonalTab(@PathVariable String siteId,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        try{
            AuthRequest ar = AuthRequest.getOrCreate(request, response);
            if(!ar.isLoggedIn()){
                return showWarningView(ar, "message.loginalert.see.page");
            }
            NGBook site = prepareSiteView(ar, siteId);
            //personal view is available to everyone, regardless of whether they
            //have a role or access to that project.  This is the page that
            //one can request access.

            request.setAttribute("realRequestURL", ar.getRequestURL());
            request.setAttribute("visibility_value", "4");
            request.setAttribute("pageTitle", site.getFullName());
            return new ModelAndView("account_personal");
        }catch(Exception ex){
            throw new NGException("nugen.operation.fail.account.personal.page", new Object[]{siteId}, ex);
        }
    }

    
//This is a pretty horrible function.  It is used to support the old style of
//auto complete when entering user names.
//However this really does not site well with the angular approach
//TODO: this should be eliminated once we know it is not being used.
    @RequestMapping(value = "/{siteId}/$/getUsers.ajax", method = RequestMethod.GET)
    public void getUsers(HttpServletRequest request, HttpServletResponse response,
            @PathVariable String siteId) throws Exception {
        try{
            AuthRequest ar = AuthRequest.getOrCreate(request, response);
            String users="";
            if(ar.isLoggedIn()){
                ar.getCogInstance().getSiteByIdOrFail(siteId);
                String matchKey = ar.defParam("matchkey", "");
                users = UserManager.getUserFullNameList(matchKey);
                users = users.replaceAll("\"", "");
            }
            NGWebUtils.sendResponse(ar, users);
        }catch(Exception ex){
            throw new NGException("nugen.operation.fail.get.users", null, ex);
        }
    }

    @RequestMapping(value = "/{siteId}/$/EditRoleBook.htm", method = RequestMethod.GET)
    public ModelAndView editRoleBook(@PathVariable String siteId,
            @RequestParam String roleName, @RequestParam String projectName,
            HttpServletRequest request,
            HttpServletResponse response)
    throws Exception {
        try{
            AuthRequest ar = AuthRequest.getOrCreate(request, response);
            if(!ar.isLoggedIn()){
                return showWarningView(ar, "message.loginalert.see.page");
            }
            prepareSiteView(ar, siteId);
            ModelAndView modelAndView = executiveCheckViews(ar);
            if (modelAndView != null) {
                return modelAndView;
            }
            modelAndView=new ModelAndView("editRoleAccount");
            request.setAttribute("headerType", "site");
            request.setAttribute("realRequestURL", ar.getRequestURL());
            request.setAttribute("roleName", roleName);
            request.setAttribute("projectName", projectName);
            return modelAndView;
        }catch(Exception ex){
            throw new NGException("nugen.operation.fail.account.editrolebook",new Object[]{siteId});
        }
    }

    @RequestMapping(value = "/{siteId}/$/replaceUsers.json", method = RequestMethod.POST)
    public void getGoalHistory(@PathVariable String siteId,
            HttpServletRequest request, HttpServletResponse response) {
        AuthRequest ar = AuthRequest.getOrCreate(request, response);
        try{
            JSONObject incoming = getPostedObject(ar);
            String sourceUser = incoming.getString("sourceUser");
            String destUser = incoming.getString("destUser");
            List<NGPageIndex> listOfSpaces = null;
            {
                NGBook ngb = ar.getCogInstance().getSiteById(siteId);
                ar.setPageAccessLevels(ngb);
                ar.assertAdmin("Must be owner of a site to replace users.");
                listOfSpaces = ar.getCogInstance().getAllProjectsInSite(siteId);
                NGPageIndex.clearLocksHeldByThisThread();
            }
            int count = 0;
            for(NGPageIndex ngpi : listOfSpaces) {
                if (!ngpi.isProject()) {
                    continue;
                }
                NGWorkspace ngw = ngpi.getPage();
                System.out.println("Changing '"+sourceUser+"' to '"+destUser+"' in ("+ngw.getFullName()+")");
                int found = ngw.replaceUserAcrossWorkspace(sourceUser, destUser);
                if (found>0) {
                    System.out.println("     found "+found+" and saving.");
                    ngpi.nextScheduledAction = ngw.nextActionDue();
                    ngw.save();
                }
                count += found;
                NGPageIndex.clearLocksHeldByThisThread();
            }
                    
            JSONObject jo = new JSONObject();
            jo.put("updated", count);
            jo.write(ar.w, 2, 2);
            ar.flush();
        }catch(Exception ex){
            Exception ee = new Exception("Unable to create Action Item for minutes of meeting.", ex);
            streamException(ee, ar);
        }
    }
    
    
    
}
