/*
 * Copyright (c) 2009--2014 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.rhn.frontend.action.user;

import com.redhat.rhn.common.localization.LocalizationService;
import com.redhat.rhn.common.security.PermissionException;
import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.frontend.struts.RequestContext;
import com.redhat.rhn.frontend.struts.RhnAction;
import com.redhat.rhn.frontend.struts.RhnHelper;
import com.redhat.rhn.manager.acl.AclManager;
import com.redhat.rhn.manager.user.UserManager;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * EnableUserSetupAction
 */
public class EnableUserSetupAction extends RhnAction {

    /** {@inheritDoc} */
    @Override
    public ActionForward execute(ActionMapping mapping,
                                 ActionForm formIn,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {

        if (!AclManager.hasAcl("user_role(org_admin)", request, null)) {
            //Throw an exception with a nice error message so the user
            //knows what went wrong.
            LocalizationService ls = LocalizationService.getInstance();
            throw new PermissionException("Only org admin's can reactivate users",
                    ls.getMessage("permission.jsp.title.enableuser"),
                    ls.getMessage("permission.jsp.summary.enableuser"));
        }

        RequestContext requestContext = new RequestContext(request);

        Long uid = requestContext.getRequiredParam("uid");

        User user = UserManager.lookupUser(requestContext.getCurrentUser(), uid);
        request.setAttribute(RhnHelper.TARGET_USER, user);

        if (!user.isDisabled()) {
            ActionMessages msg = new ActionMessages();
            msg.add(ActionMessages.GLOBAL_MESSAGE,
                new ActionMessage("userenable.error.usernotdisabled",
                   StringEscapeUtils.escapeHtml4(user.getLogin())));
            getStrutsDelegate().saveMessages(request, msg);
        }

        return mapping.findForward(RhnHelper.DEFAULT_FORWARD);
    }
}
