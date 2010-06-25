/**
 * Copyright (c) 2000-2010 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.knowledgebase.util;

import com.liferay.knowledgebase.model.Article;
import com.liferay.knowledgebase.model.ArticleConstants;
import com.liferay.knowledgebase.service.ArticleLocalServiceUtil;
import com.liferay.knowledgebase.service.ArticleServiceUtil;
import com.liferay.knowledgebase.util.PortletKeys;
import com.liferay.portal.kernel.bean.BeanPropertiesUtil;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.io.unsync.UnsyncStringReader;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.DiffHtmlUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HttpUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.PortalClassInvoker;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.Layout;
import com.liferay.portal.model.LayoutConstants;
import com.liferay.portal.model.PortletConstants;
import com.liferay.portal.service.LayoutLocalServiceUtil;
import com.liferay.portal.util.PortalUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.portlet.PortletPreferences;
import javax.portlet.WindowState;

/**
 * <a href="KnowledgeBaseUtil.java.html"><b><i>View Source</i></b></a>
 *
 * @author Peter Shin
 * @author Brian Wing Shun Chan
 */
public class KnowledgeBaseUtil {

	public static String getArticleDiff(
			long resourcePrimKey, int sourceVersion, int targetVersion,
			String parameter, String portalURL)
		throws Exception {

		if (sourceVersion == targetVersion) {
			Article article = ArticleLocalServiceUtil.getArticle(
				resourcePrimKey, targetVersion);

			Object object = BeanPropertiesUtil.getObject(article, parameter);

			return String.valueOf(object);
		}

		Article sourceArticle = ArticleLocalServiceUtil.getArticle(
			resourcePrimKey, sourceVersion);
		Article targetArticle = ArticleLocalServiceUtil.getArticle(
			resourcePrimKey, targetVersion);

		Object sourceObject = BeanPropertiesUtil.getObject(
			sourceArticle, parameter);
		Object targetObject = BeanPropertiesUtil.getObject(
			targetArticle, parameter);

		String sourceHtml = String.valueOf(sourceObject);
		String targetHtml = String.valueOf(targetObject);

		return getDiff(sourceHtml, targetHtml, portalURL);
	}

	public static String getArticleDiff(
			long resourcePrimKey, int version, String parameter,
			String portalURL)
		throws Exception {

		int sourceVersion = version;
		int targetVersion = version;

		if (sourceVersion != ArticleConstants.DEFAULT_VERSION) {
			sourceVersion = sourceVersion - 1;
		}

		return getArticleDiff(
			resourcePrimKey, sourceVersion, targetVersion, parameter,
			portalURL);
	}

	public static List<Article> getArticles(
			long[] resourcePrimKeys, int start, int end,
			boolean checkPermission)
		throws Exception {

		if ((start == QueryUtil.ALL_POS) && (end == QueryUtil.ALL_POS)) {
			start = 0;
			end = resourcePrimKeys.length;
		}

		List<Long> selResourcePrimKeys = new ArrayList<Long>();

		for (int i = start; (i < end) && (i < resourcePrimKeys.length); i++) {
			selResourcePrimKeys.add(resourcePrimKeys[i]);
		}

		Map<String, Object> params = new HashMap<String, Object>();

		params.put(
			"resourcePrimKey",
			selResourcePrimKeys.toArray(new Long[selResourcePrimKeys.size()]));

		List<Article> unsortedArticles = null;

		if (checkPermission) {
			unsortedArticles = ArticleServiceUtil.getArticles(
				params, false, QueryUtil.ALL_POS, QueryUtil.ALL_POS, null);
		}
		else {
			unsortedArticles = ArticleLocalServiceUtil.getArticles(
				params, false, QueryUtil.ALL_POS, QueryUtil.ALL_POS, null);
		}

		unsortedArticles = ListUtil.copy(unsortedArticles);

		List<Article> articles = new ArrayList<Article>();

		for (Long resourcePrimKey : selResourcePrimKeys) {
			for (int i = 0; i < unsortedArticles.size(); i++) {
				Article article = unsortedArticles.get(i);

				if (article.getResourcePrimKey() == resourcePrimKey) {
					articles.add(article);
					unsortedArticles.remove(article);

					break;
				}
			}
		}

		return articles;
	}

	public static String getArticleURL(
			String portletId, long resourcePrimKey, String portalURL)
		throws Exception {

		Object[] plidAndWindowState = getPlidAndWindowState(
			portletId, resourcePrimKey, portalURL);

		long plid = (Long)plidAndWindowState[0];
		WindowState windowState = (WindowState)plidAndWindowState[1];

		if (plid == LayoutConstants.DEFAULT_PLID) {
			return StringPool.BLANK;
		}

		boolean isMaximized = windowState.equals(WindowState.MAXIMIZED);

		Layout layout = LayoutLocalServiceUtil.getLayout(plid);

		String layoutActualURL = PortalUtil.getLayoutActualURL(layout);
		String layoutFullURL = portalURL + layoutActualURL;

		return getArticleURL(
			portletId, resourcePrimKey, layoutFullURL, isMaximized);
	}

	public static String getArticleURL(
		String portletId, long resourcePrimKey, String layoutFullURL,
		boolean isMaximized) {

		String pluginId = PortletConstants.getRootPortletId(portletId);
		String namespace = PortalUtil.getPortletNamespace(portletId);

		String jspPage = null;

		if (pluginId.equals(PortletKeys.KNOWLEDGE_BASE_ADMIN)) {
			jspPage = "/admin/view_article.jsp";
		}
		else if (pluginId.equals(PortletKeys.KNOWLEDGE_BASE_AGGREGATOR)) {
			jspPage = "/aggregator/view_article.jsp";
		}

		String articleURL = layoutFullURL;

		if (isMaximized) {
			articleURL = HttpUtil.setParameter(
				articleURL, "p_p_state", WindowState.MAXIMIZED.toString());
		}

		articleURL = HttpUtil.setParameter(articleURL, "p_p_id", portletId);
		articleURL = HttpUtil.setParameter(
			articleURL, namespace + "jspPage", jspPage);
		articleURL = HttpUtil.setParameter(
			articleURL, namespace + "resourcePrimKey", resourcePrimKey);

		return articleURL;
	}

	protected static Object[] getAdminPlidAndWindowState(
			String portletId, long resourcePrimKey)
		throws Exception {

		Article article = ArticleLocalServiceUtil.getLatestArticle(
			resourcePrimKey);

		long plid = PortalUtil.getPlidFromPortletId(
			article.getGroupId(), portletId);

		return new Object[] {plid, WindowState.NORMAL};
	}

	protected static Object[] getAggregatorPlidAndWindowState(
			String portletId, long resourcePrimKey)
		throws Exception {

		Article article = ArticleLocalServiceUtil.getLatestArticle(
			resourcePrimKey);

		long parentGroupId = PortalUtil.getParentGroupId(article.getGroupId());

		long plid = PortalUtil.getPlidFromPortletId(parentGroupId, portletId);
		WindowState windowState = WindowState.NORMAL;

		if (plid == LayoutConstants.DEFAULT_PLID) {
			return new Object[] {plid, windowState};
		}

		Object[] arguments = new Object[] {
			LayoutLocalServiceUtil.getLayout(plid), portletId, StringPool.BLANK
		};

		PortletPreferences jxPreferences =
			(PortletPreferences)PortalClassInvoker.invoke(
				"com.liferay.portlet.PortletPreferencesFactoryUtil",
				"getPortletSetup", arguments);

		String articleWindowState = jxPreferences.getValue(
			"article-window-state", WindowState.MAXIMIZED.toString());
		String selectionMethod = jxPreferences.getValue(
			"selection-method", "parent-group");
		long[] resourcePrimKeys = GetterUtil.getLongValues(
			jxPreferences.getValues("resource-prim-keys", new String[0]));
		long[] scopeGroupIds = GetterUtil.getLongValues(
			jxPreferences.getValues("scope-group-ids", new String[0]));

		boolean hasResourcePrimKey = ArrayUtil.contains(
			resourcePrimKeys, resourcePrimKey);
		boolean hasScopeGroup = ArrayUtil.contains(
			scopeGroupIds, article.getGroupId());

		if (articleWindowState.equals(WindowState.MAXIMIZED.toString())) {
			windowState = WindowState.MAXIMIZED;
		}

		if ((selectionMethod.equals("parent-group")) ||
			(selectionMethod.equals("articles") && hasResourcePrimKey) ||
			(selectionMethod.equals("scope-groups") && hasScopeGroup)) {

			return new Object[] {plid, windowState};
		}

		if (selectionMethod.equals("articles")) {

			// Retrieving all parent and children articles for each selected
			// article can be expensive. Skip this check for better performance.

			return new Object[] {plid, windowState};
		}

		return new Object[] {LayoutConstants.DEFAULT_PLID, WindowState.NORMAL};
	}

	protected static String getDiff(
			String sourceHtml, String targetHtml, String portalURL)
		throws Exception {

		String diff = DiffHtmlUtil.diff(
			new UnsyncStringReader(sourceHtml),
			new UnsyncStringReader(targetHtml));

		diff = StringUtil.replace(
			diff,
			new String[] {
				"changeType=\"diff-added-image\"",
				"changeType=\"diff-changed-image\"",
				"changeType=\"diff-removed-image\"",
				"class=\"diff-html-added\"",
				"class=\"diff-html-changed\"",
				"class=\"diff-html-removed\""
			},
			new String[] {
				"style=\"border: 10px solid #CFC;\"",
				"style=\"border: 10px solid #C6C6FD;\"",
				"style=\"border: 10px solid #FDC6C6;\"",
				"style=\"background-color: #CFC;\"",
				"style=\"background-color: #C6C6FD\"",
				"style=\"background-color: #FDC6C6; " +
					"text-decoration: line-through;\""
			});

		if (Validator.isNotNull(portalURL)) {
			diff = StringUtil.replace(
				diff,
				new String[] {
					"href=\"/",
					"src=\"/"
				},
				new String[] {
					"href=\"" + portalURL + "/",
					"src=\"" + portalURL + "/"
				});
		}

		int i = diff.indexOf("<img ");

		while (i != -1) {
			String oldImg = diff.substring(i, diff.indexOf("/>", i) + 2);

			int x = oldImg.indexOf("style=\"");
			int y = oldImg.indexOf("style=\"", x + 7);
			int z = oldImg.indexOf(StringPool.QUOTE, y + 7);

			if (y != -1) {
				String style = oldImg.substring(y, z + 1);

				String newImg = StringUtil.replace(
					oldImg,
					new String[] {
						style,
						"style=\""
					},
					new String[] {
						StringPool.BLANK,
						style.substring(0, style.length() - 1)
					});

				diff = StringUtil.replace(diff, oldImg, newImg);
			}

			i = diff.indexOf("<img ", i + 5);
		}

		return diff;
	}

	protected static Object[] getPlidAndWindowState(
			String portletId, long resourcePrimKey, String portalURL)
		throws Exception {

		String pluginId = PortletConstants.getRootPortletId(portletId);

		if (pluginId.equals(PortletKeys.KNOWLEDGE_BASE_ADMIN)) {
			return getAdminPlidAndWindowState(portletId, resourcePrimKey);
		}
		else if (pluginId.equals(PortletKeys.KNOWLEDGE_BASE_AGGREGATOR)) {
			return getAggregatorPlidAndWindowState(portletId, resourcePrimKey);
		}

		return new Object[] {LayoutConstants.DEFAULT_PLID, WindowState.NORMAL};
	}

}