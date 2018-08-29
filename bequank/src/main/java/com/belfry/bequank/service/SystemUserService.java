package com.belfry.bequank.service;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;

/**
 * 系统用户api
 */
public interface SystemUserService {
    JSONObject postTutorial(HttpServletRequest request, String nickname, Long userid, String title, String discription, JSONArray keywords, String content, String time);
}