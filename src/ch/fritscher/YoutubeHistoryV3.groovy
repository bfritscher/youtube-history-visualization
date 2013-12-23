/*
 * Google Youtube API sample, modified to parse and save history to DB
 *
 * Copyright (c) 2012 Google Inc
 * Changes Copyright (c) 2013 Boris Fritscher
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package ch.fritscher;

import java.io.File;
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar;
import java.util.List;
import org.joda.time.Duration
import org.joda.time.Period

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.FileCredentialStore;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Activity;
import com.google.api.services.youtube.model.ActivityContentDetails;
import com.google.api.services.youtube.model.ActivityContentDetails.Bulletin;
import com.google.api.services.youtube.model.ActivitySnippet;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.PlaylistItemListResponse
import com.google.api.services.youtube.model.ResourceId;
import com.google.api.services.youtube.model.VideoCategoryListResponse;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.common.collect.Lists;
import groovy.sql.Sql

public class YoutubeHistoryV3 {

  /** Global instance of the HTTP transport. */
  private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

  /** Global instance of the JSON factory. */
  private static final JsonFactory JSON_FACTORY = new JacksonFactory();

  /** Global instance of YouTube object to make all API requests. */
  private static YouTube youtube;
  
  private static Sql db;
  
  
  public static java.util.Date parseRFC3339Date(String datestring) throws java.text.ParseException, IndexOutOfBoundsException{
	  if(datestring == null) return null
	  
	  Date d = new Date();
	  
		  //if there is no time zone, we don't need to do any special parsing.
	  if(datestring.endsWith("Z")){
		try{
		  SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");//spec for RFC3339
		  d = s.parse(datestring);
		}
		catch(java.text.ParseException pe){//try again with optional decimals
		  SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'");//spec for RFC3339 (with fractional seconds)
		  s.setLenient(true);
		  d = s.parse(datestring);
		}
		return d;
	  }
  
		   //step one, split off the timezone.
	  String firstpart = datestring.substring(0,datestring.lastIndexOf('-'));
	  String secondpart = datestring.substring(datestring.lastIndexOf('-'));
		  
			//step two, remove the colon from the timezone offset
	  secondpart = secondpart.substring(0,secondpart.indexOf(':')) + secondpart.substring(secondpart.indexOf(':')+1);
	  datestring  = firstpart + secondpart;
	  SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");//spec for RFC3339
	  try{
		d = s.parse(datestring);
	  }
	  catch(java.text.ParseException pe){//try again with optional decimals
		s = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ");//spec for RFC3339 (with fractional seconds)
		s.setLenient(true);
		d = s.parse(datestring);
	  }
	  return d;
	}
  

  /**
   * Authorizes the installed application to access user's protected data.
   *
   * @param scopes list of scopes needed to run upload.
   */
  private static Credential authorize(List<String> scopes) throws Exception {

    // Load client secrets.
    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
        JSON_FACTORY, YoutubeHistoryV3.class.getResourceAsStream("/client_secrets.json"));

    // Set up file credential store.
    FileCredentialStore credentialStore = new FileCredentialStore(
        new File(System.getProperty("user.home"), ".credentials/youtube-api-channelbulletin.json"),
        JSON_FACTORY);

    // Set up authorization code flow.
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, scopes).setCredentialStore(credentialStore)
        .build();

    // Build the local server and bind it to port 8080
    LocalServerReceiver localReceiver = new LocalServerReceiver.Builder().setPort(8080).build();

    // Authorize.
    return new AuthorizationCodeInstalledApp(flow, localReceiver).authorize("user");
  }

  static def getHistoryPlaylistID(){
	  /*
	   * Now that the user is authenticated, the app makes a channel list request to get the
	   * authenticated user's channel. https://developers.google.com/youtube/v3/docs/channels/list
	   */
	  YouTube.Channels.List channelRequest = youtube.channels().list("contentDetails");
	  channelRequest.setMine(true);
	  /*
	   * Limits the results to only the data we need making your app more efficient.
	   */
	  channelRequest.setFields("items/contentDetails");
	  ChannelListResponse channelResult = channelRequest.execute();

	  /*
	   * Gets the list of channels associated with the user.
	   */
	  
	  
	  List<Channel> channelsList = channelResult.getItems();

	  return channelsList[0].contentDetails.relatedPlaylists.watchHistory
  }
  
  
  static def saveHistory(histroyPlaylistID, pageToken=""){
	  YouTube.PlaylistItems.List itemsRequest = youtube.playlistItems().list("snippet")
	  itemsRequest.setPlaylistId(historyPlaylistID)
	  itemsRequest.setMaxResults(50)
	  itemsRequest.setPageToken(pageToken)
	  PlaylistItemListResponse itemsResult = itemsRequest.execute()
	  println itemsResult.getPageInfo()
	  for(def item : itemsResult.getItems()){
		  
		  println item.snippet.title
		  def video = getVideoDetail(item.snippet.resourceId.videoId)
		  try{
			  def uploadeddate = parseRFC3339Date(video.uploadeddate?.toStringRfc3339())?.time
			  db.executeInsert( "INSERT INTO historyv3 (id, title, video_id, watchdate, duration, uploader, uploadeddate, description, category_id, views) "
				  + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", [
						  item.id,
						  item.snippet.title,
						  item.snippet.resourceId.videoId,
						  new Timestamp(parseRFC3339Date(item.snippet.publishedAt.toStringRfc3339())?.time),
						  video.duration,
						  video.uploader,
						  uploadeddate? new Timestamp(uploadeddate) : null,
						  video.description,
						  video.categoryID as Integer,
						  video.views as Integer
					  ])
		  }catch(ex){
		  	if(!ex.message.contains("ERROR: duplicate key")){
				  ex.printStackTrace()
			}
			return null
		  }
	  }
	  
	  //if has nextpage get nextpage or if already parsed
	  pageToken = itemsResult.getNextPageToken()
	  if(pageToken){
		  saveHistory(histroyPlaylistID, pageToken)
	  }
  }
  
  static def getVideoDetail(videoID){
	  YouTube.Videos.List request = youtube.videos().list("contentDetails,snippet,statistics")
	  request.setId(videoID)
	  VideoListResponse result = request.execute()
	  def video = result.getItems()[0]
	  try{
		  
	  
	  return [duration: new Period(video?.contentDetails?.duration)?.toStandardSeconds()?.seconds,
			  uploader: video?.snippet.channelId,
			  uploadeddate: video?.snippet.publishedAt,
			  description: video?.snippet.description,
			  categoryID: video?.snippet.categoryId,
		      views: video?.statistics.viewCount]
	  }catch(java.lang.NullPointerException ex){
	  	return [duration: null,
			  uploader: null,
			  uploadeddate: null,
			  description: null,
			  categoryID: null,
		      views: null]
	  }
  }
  
  static def updateCategories(){
	  db.eachRow("""SELECT h.category_id as cid
	  FROM historyv3 h
	  LEFT JOIN categories c ON h.category_id = c.id
	  WHERE c.id IS NULL
	  GROUP BY h.category_id
	  ORDER BY h.category_id"""){
	  	getCategoryTitle(it.cid)
	  }
  }
  
  static def getCategoryTitle(categoryID){
	  try{
		  YouTube.VideoCategories.List request =  youtube.videoCategories().list("snippet")
		  request.setId(categoryID as String)
		  VideoCategoryListResponse result = request.execute()
		  println result.getItems()[0].snippet.title
		  db.executeInsert( "INSERT INTO categories (id, title) "
			  + "VALUES (?, ?)", [
					  categoryID,
					  result.getItems()[0].snippet.title
				  ])
	  }catch(ex){
	  }
  }
  
  static def updateUploaders(){
	  db.eachRow("""SELECT h.uploader as cid
	  FROM historyv3 h
	  LEFT JOIN uploaders c ON h.uploader = c.id
	  WHERE c.id IS NULL
	  GROUP BY h.uploader
	  ORDER BY h.uploader"""){
		  getUploaderTitle(it.cid)
	  }
  }
  
  static def getUploaderTitle(uploaderID){
	  try{
		  YouTube.Channels.List request =  youtube.channels().list("snippet")
		  request.setId(uploaderID as String)
		  ChannelListResponse result = request.execute()
		  println result.getItems()[0].snippet.title
		  db.executeInsert( "INSERT INTO uploaders (id, title) "
			  + "VALUES (?, ?)", [
					  uploaderID,
					  result.getItems()[0].snippet.title
				  ])
	  }catch(ex){
	  }
  }
  
  static def updateId(){
	  db.eachRow("""SELECT DISTINCT uploader FROM watchentries"""){
		  getUploaderTitleByUsername(it.uploader)
	  }
  }
  
  static def getUploaderTitleByUsername(uploaderID){
	  try{
		  YouTube.Channels.List request =  youtube.channels().list("snippet")
		  request.set("forUsername", uploaderID as String)
		  ChannelListResponse result = request.execute()
		  println result.getItems()[0].snippet.title
		  db.executeInsert( "UPDATE watchentries SET uploader_id = ? WHERE uploader = ? ", [
					  result.getItems()[0].id,
					 uploaderID
				  ])
		  db.executeInsert( "INSERT INTO uploaders (id, title) "
			  + "VALUES (?, ?)", [
					  result.getItems()[0].id,
					  result.getItems()[0].snippet.title
				  ])
		  
	  }catch(ex){
	  }
  }
  
  /**
   * Authorizes user, runs Youtube.Channnels.List to get the default channel, and posts a bulletin
   * with a video id to the user's default channel.
   *
   * @param args command line args (not used).
   */
  public static void main(String[] args) {

    // Scope required to upload to YouTube.
    List<String> scopes = Lists.newArrayList("https://www.googleapis.com/auth/youtube");

    try {
      // Authorization.
      Credential credential = authorize(scopes);

      // YouTube object used to make all API requests.
      youtube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(
          "youtube-cmdline-channelbulletin-sample").build();

	  //db
	  Properties props = new Properties()
	  props.setProperty('user', 'youtube')
	  props.setProperty('password', '')
	  db = Sql.newInstance('jdbc:postgresql://localhost/youtube', props , 'org.postgresql.Driver')
	  
	  
	  def histroyPlaylistID = getHistoryPlaylistID()
      saveHistory(histroyPlaylistID)

	  //category parser
	  updateCategories()
	  updateUploaders()
	  updateId()
	  
    } catch (GoogleJsonResponseException e) {
      e.printStackTrace();
      System.err.println("There was a service error: " + e.getDetails().getCode() + " : "
          + e.getDetails().getMessage());

    } catch (Throwable t) {
      t.printStackTrace();
    }
  }
}