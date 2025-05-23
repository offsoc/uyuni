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
package com.redhat.rhn.frontend.action.token;

import com.redhat.rhn.common.localization.LocalizationService;
import com.redhat.rhn.common.validator.ValidatorException;
import com.redhat.rhn.domain.channel.Channel;
import com.redhat.rhn.domain.channel.ChannelFactory;
import com.redhat.rhn.domain.entitlement.Entitlement;
import com.redhat.rhn.domain.org.Org;
import com.redhat.rhn.domain.server.ContactMethod;
import com.redhat.rhn.domain.server.ServerFactory;
import com.redhat.rhn.domain.server.ServerGroupType;
import com.redhat.rhn.domain.token.ActivationKey;
import com.redhat.rhn.domain.token.ActivationKeyFactory;
import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.frontend.struts.LabelValueEnabledBean;
import com.redhat.rhn.frontend.struts.RequestContext;
import com.redhat.rhn.frontend.struts.RhnAction;
import com.redhat.rhn.frontend.struts.RhnHelper;
import com.redhat.rhn.frontend.struts.RhnValidationHelper;
import com.redhat.rhn.manager.channel.ChannelManager;
import com.redhat.rhn.manager.token.ActivationKeyManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.apache.struts.action.DynaActionForm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * ActivationKeyDetailsAction
 */
public class ActivationKeyDetailsAction extends RhnAction {
    private static final String DESCRIPTION = "description";

    private static final String KEY = "key";
    private static final String USAGE_LIMIT = "usageLimit";
    private static final String SELECTED_BASE_CHANNEL = "selectedBaseChannel";
    private static final String SELECTED_CHILD_CHANNELS = "childChannels";
    private static final String POSSIBLE_ENTS = "possibleEntitlements";
    private static final String SELECTED_ENTS = "selectedEntitlements";
    private static final String ORG_DEFAULT = "universal";
    private static final String AUTO_DEPLOY = "autoDeploy";
    private static final Long DEFAULT_CHANNEL_ID = -1L;
    private static final String EDIT_MODE = "edit";
    private static final String CREATE_MODE = "create";
    private static final String PREFIX = "prefix";
    private static final String UNPREFIXED = "unprefixed";
    private static final String BLANK_DESCRIPTION = "blankDescription";

    private static final String CONTACT_METHODS = "contactMethods";
    private static final String CONTACT_METHOD = "contactMethodId";

    /** {@inheritDoc} */
    @Override
    public ActionForward execute(ActionMapping mapping,
                                 ActionForm formIn,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        RequestContext context = new RequestContext(request);
        DynaActionForm form = (DynaActionForm) formIn;
        if (CREATE_MODE.equals(mapping.getParameter())) {
            request.setAttribute(CREATE_MODE, Boolean.TRUE);
        }

        request.setAttribute(PREFIX,
                ActivationKey.makePrefix(context.getCurrentUser().getOrg()));
        request.setAttribute(BLANK_DESCRIPTION,
                                ActivationKeyFactory.DEFAULT_DESCRIPTION);

        if (context.isSubmitted()) {
            try {
                ActionErrors errors = RhnValidationHelper.validateDynaActionForm(this,
                                            form);
                if (!StringUtils.isBlank(form.getString(KEY)) &&
                    !ActivationKey.isValid(form.getString(KEY))) {
                    ActionMessages msg = new ActionMessages();
                    addToMessage(msg, "activation-key.java.allowed-values");
                    errors.add(msg);
                }

                if (!errors.isEmpty()) {
                        getStrutsDelegate().saveMessages(request, errors);
                        return handleFailure(mapping, context);
                }
                Map<String, Object> params = new HashMap<>();

                if (CREATE_MODE.equals(mapping.getParameter())) {
                    ActivationKey key = create(form, context);
                    params.put(RequestContext.TOKEN_ID, key.getId().toString());
                }
                else {

                    ActivationKey key =  update(form, context);
                    params.put(RequestContext.TOKEN_ID, key.getId().toString());
                }
                return getStrutsDelegate().forwardParams(
                        mapping.findForward("success"), params);
            }
            catch (ValidatorException ve) {
                if (null == ve.getResult()) {
                    getStrutsDelegate().saveMessage(ve.getMessage(), request);
                }
                else {
                    getStrutsDelegate().saveMessages(request, ve.getResult());
                }
                return handleFailure(mapping, context);
            }
        }
        setupEntitlements(context);
        setupContactMethods(context);
        if (EDIT_MODE.equals(mapping.getParameter())) {
            ActivationKey key = context.lookupAndBindActivationKey();

            populateForm(form, key, context);
        }
        return mapping.findForward(RhnHelper.DEFAULT_FORWARD);

    }


    /**
     * @param mapping action mapping parameter
     * @param context the request context
     * @return the Action forward
     */
    private ActionForward handleFailure(ActionMapping mapping,
                                            RequestContext context) {
        RhnValidationHelper.setFailedValidation(context.getRequest());
        setupEntitlements(context);
        setupContactMethods(context);

        if (EDIT_MODE.equals(mapping.getParameter())) {
            Map<String, Object> params = new HashMap<>();
            params.put(RequestContext.TOKEN_ID,
                        context.getParam(RequestContext.TOKEN_ID, true));
            return getStrutsDelegate().forwardParams(
                    mapping.findForward(RhnHelper.DEFAULT_FORWARD), params);
        }

        return mapping.findForward(RhnHelper.DEFAULT_FORWARD);
    }

    /**
     * Calculate the list of entitlements to remove based on what *wasn't* checked when
     * the form was submitted.
     */
    private List<String> entitlementsToRemove(Org org, List<String> selectedEntitlements) {
        List<String> removeThese = new LinkedList<>();
        for (Entitlement ent : org.getValidAddOnEntitlementsForOrg()) {
            if (!selectedEntitlements.contains(ent.getLabel())) {
                removeThese.add(ent.getLabel());
            }
        }
        return removeThese;
    }

    private ActivationKey update(DynaActionForm form, RequestContext context) {
        User user = context.getCurrentUser();
        ActivationKeyManager manager = ActivationKeyManager.getInstance();
        ActivationKey key = context.lookupAndBindActivationKey();

        String[] selected = (String[])form.get(SELECTED_ENTS);
        List<String> selectedList = Arrays.asList(selected);
        if (selected != null) {
            manager.removeEntitlements(key, entitlementsToRemove(user.getOrg(),
                selectedList));
            manager.addEntitlements(key, selectedList);
        }

        if (StringUtils.isBlank(form.getString(DESCRIPTION))) {
            key.setNote(ActivationKeyFactory.DEFAULT_DESCRIPTION);
        }
        else {
            key.setNote(form.getString(DESCRIPTION));
        }

        key.setBaseChannel(lookupChannel(form, user));
        updateChildChannels(form, key);

        key.getToken().setOrgDefault(Boolean.TRUE.equals(form.get(ORG_DEFAULT)));

        Long usageLimit = null;
        if (!StringUtils.isBlank(form.getString(USAGE_LIMIT))) {
            usageLimit = Long.valueOf(form.getString(USAGE_LIMIT));
        }

        key.setDeployConfigs(Boolean.TRUE.equals(form.get(AUTO_DEPLOY)));

        key.setUsageLimit(usageLimit);

        // Set the contact method
        long contactId = (Long) form.get(CONTACT_METHOD);
        if (contactId != key.getContactMethod().getId()) {
            key.setContactMethod(ServerFactory.findContactMethodById(contactId));
        }

        ActivationKeyFactory.save(key);
        ActionMessages msg = new ActionMessages();
        addToMessage(msg, "activation-key.java.modified", key.getNote());

        String newKey = form.getString(KEY);
        if (StringUtils.isBlank(newKey)) {
            newKey = ActivationKeyFactory.generateKey();
        }
        newKey = ActivationKey.sanitize(key.getOrg(), newKey);
        String enteredKey = form.getString(KEY);
        if (!enteredKey.equals(key.getKey()) && !newKey.equals(key.getKey())) {
            manager.changeKey(newKey, key, user);
            if (!StringUtils.isBlank(enteredKey) &&
                        !enteredKey.equals(key.getKey())) {
                addToMessage(msg, "activation-key.java.org_prefixed",
                                                        key, newKey);
            }

        }
        getStrutsDelegate().saveMessages(context.getRequest(), msg);
        return key;
    }

    private void populateForm(DynaActionForm form, ActivationKey key,
                                                RequestContext context) {
        context.getRequest().setAttribute(DESCRIPTION, key.getNote());
        form.set(DESCRIPTION, key.getNote());
        setupKey(form, key, context);

        if (key.getUsageLimit() != null) {
            form.set(USAGE_LIMIT, String.valueOf(key.getUsageLimit()));
        }

        form.set(ORG_DEFAULT, key.isUniversalDefault());
        List<String> entitlements = new ArrayList<>();
        for (ServerGroupType type : key.getEntitlements()) {
            entitlements.add(type.getLabel());
        }
        form.set(SELECTED_ENTS, entitlements.toArray(new String[0]));

        form.set(AUTO_DEPLOY, key.getDeployConfigs());

        // Set the contact method
        form.set(CONTACT_METHOD, key.getContactMethod().getId());
    }

    private void setupKey(DynaActionForm form, ActivationKey key,
                                                RequestContext context) {
        String orgPrefix = ActivationKey.makePrefix(key.getOrg());
        if (key.getKey().startsWith(orgPrefix)) {
            form.set(KEY, key.getKey().substring(orgPrefix.length()));
        }
        else {
            form.set(KEY, key.getKey());
            context.getRequest().setAttribute(UNPREFIXED, Boolean.TRUE);
        }
    }

    private void setupEntitlements(RequestContext context) {
        Org org = context.getCurrentUser().getOrg();
        Set<LabelValueEnabledBean> entWidgets = new
                TreeSet<>();
        context.getRequest().setAttribute(POSSIBLE_ENTS, entWidgets);
        for (Entitlement ent : org.getValidAddOnEntitlementsForOrg()) {
            entWidgets.add(lve(ent.getHumanReadableLabel(), ent.getLabel(), false));
        }
    }


    private ActivationKey create(DynaActionForm daForm, RequestContext context) {
        User user = context.getCurrentUser();
        ActivationKeyManager manager = ActivationKeyManager.getInstance();
        /**
         * createNewActivationKey(User user,
            String key, String note, Long usageLimit, Channel baseChannel,
            boolean universalDefault
         */

        Long usageLimit = null;
        if (!StringUtils.isBlank(daForm.getString(USAGE_LIMIT))) {
            usageLimit = Long.valueOf(daForm.getString(USAGE_LIMIT));
        }

        List<String> selected = Arrays.asList((String[])daForm.get(SELECTED_ENTS));
        manager.validateAddOnEntitlements(selected, true);

        ActivationKey key = manager.createNewActivationKey(user,
                                daForm.getString(KEY),
                                daForm.getString(DESCRIPTION),
                                usageLimit,
                                lookupChannel(daForm, user),
                                Boolean.TRUE.equals(daForm.get(ORG_DEFAULT)));

        updateChildChannels(daForm, key);

        // Set the contact method
        long contactId = (Long) daForm.get(CONTACT_METHOD);
        if (contactId != key.getContactMethod().getId()) {
            key.setContactMethod(ServerFactory.findContactMethodById(contactId));
        }

        if (selected != null) {
            manager.addEntitlements(key, selected);
        }
        ActionMessages msg = new ActionMessages();
        addToMessage(msg, "activation-key.java.created", key.getNote());
        getStrutsDelegate().saveMessages(context.getRequest(), msg);
        return key;
    }


    private Channel lookupChannel(DynaActionForm daForm, User user) {
        Long selectedChannel = (Long)daForm.get(SELECTED_BASE_CHANNEL);
        if (selectedChannel == null) {
            throw new ValidatorException(
                    LocalizationService.getInstance().getMessage("activation-key.java.nochannel"));
        }
        if (!DEFAULT_CHANNEL_ID.equals(selectedChannel)) {
            return ChannelManager.lookupByIdAndUser(
                                selectedChannel, user);
        }
        return null;
    }

    private void addToMessage(ActionMessages msgs, String key, Object... args) {
        ActionMessage temp =  new ActionMessage(key, args);
        msgs.add(ActionMessages.GLOBAL_MESSAGE, temp);
    }

    /**
     * Put the list of contact methods to the request.
     */
    protected void setupContactMethods(RequestContext context) {
        List<ContactMethod> contactMethods = ServerFactory.listContactMethods();
        context.getRequest().setAttribute(CONTACT_METHODS, contactMethods);
    }

    /**
     * Update child channels with the selection set from the received form
     *
     * @param form the source form
     * @param key the activation key to update with the selected child channels
     */
    private void updateChildChannels(DynaActionForm form, ActivationKey key) {
        Channel base = key.getBaseChannel();
        key.clearChannels(); // clear the current selection
        if (base != null) {
            key.addChannel(base); // re-add the base channel
        }
        for (String id : (String[])form.get(SELECTED_CHILD_CHANNELS)) {
            key.addChannel(ChannelFactory.lookupById(Long.parseLong(id.trim()))); // add all selected child channels
        }
        ActivationKeyFactory.save(key);
    }
}
