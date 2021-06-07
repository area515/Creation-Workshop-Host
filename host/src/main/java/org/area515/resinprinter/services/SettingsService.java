package org.area515.resinprinter.services;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import java.util.Iterator;
import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.jws.WebParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.area515.resinprinter.server.CwhEmailSettings;
import org.area515.resinprinter.server.HostInformation;
import org.area515.resinprinter.server.HostProperties;
import org.area515.resinprinter.server.Skin;
import org.area515.resinprinter.util.security.PhotonicUser;

import com.fasterxml.jackson.core.io.JsonStringEncoder;

@Api(value="settings")
@RolesAllowed(PhotonicUser.FULL_RIGHTS)
@Path("settings")
public class SettingsService {
	public static SettingsService INSTANCE = new SettingsService();
	
	private SettingsService(){}
	
    @ApiOperation(value="This method returns a hint towards the visible cards that should be shown in the GUI. The config.properties key for this is: visibleCards")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = SwaggerMetadata.SUCCESS),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	//TODO: This should be limited by user permissions...
	@GET
	@Path("visibleCards")
	@Produces(MediaType.APPLICATION_JSON)
	public List<String> getVisibleCards() {
		return HostProperties.Instance().getVisibleCards();
	}
	
    @Deprecated
    @ApiOperation(value="Returns the integer based version number of Photonic3D found in the build.number file.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = SwaggerMetadata.SUCCESS),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("integerVersion")
	@Produces(MediaType.APPLICATION_JSON)
	public int getIntegerVersion() {
		return HostProperties.Instance().getVersionNumber();
	}

	@ApiOperation(value="Returns the string release tag (repo.version) of Photonic3D found in the build.number file.")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = SwaggerMetadata.SUCCESS),
			@ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("releaseTagName")
	@Produces(MediaType.APPLICATION_JSON)
	public String getReleaseTagName() {
		return "\"" + new String(JsonStringEncoder.getInstance().quoteAsString(HostProperties.Instance().getReleaseTagName())) + "\"";
	}

	@ApiOperation(value="Returns the GitHub repo name to load printer profiles from.")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = SwaggerMetadata.SUCCESS),
			@ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("printerProfileRepo")
	@Produces(MediaType.APPLICATION_JSON)
	public String getPrinterProfileRepo() {
		return "\"" + new String(JsonStringEncoder.getInstance().quoteAsString(HostProperties.Instance().getPrinterProfileRepo())) + "\"";
	}

	@ApiOperation(value="Get the email settings that are setup for the NotificationManager and taking diagnostics.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = SwaggerMetadata.SUCCESS),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("emailSettings")
	@Produces(MediaType.APPLICATION_JSON)
	public CwhEmailSettings getEmailSettings() {
		return HostProperties.Instance().loadEmailSettings();
	}
    
    @ApiOperation(value="Allows the email settings to be setup for the NotificationManager and taking diagnostics.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = SwaggerMetadata.SUCCESS),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@PUT
	@Path("emailSettings")
	@Consumes(MediaType.APPLICATION_JSON)
	public void setEmailSettings(CwhEmailSettings settings) {
		HostProperties.Instance().saveEmailSettings(settings);
	}
	
    @ApiOperation(value="This will return the host information(device name and manufacturer) that is setup in config.properties. "
    		+ "This host information is also what identifies Photonic 3D printers through multicast to DLNA and UPNP clients on the user's network.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = SwaggerMetadata.SUCCESS),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("hostInformation")
	@Produces(MediaType.APPLICATION_JSON)
	public HostInformation getHostInformation() {
		return HostProperties.Instance().loadHostInformation();
	}

    @ApiOperation(value="This will set the host information(device name and manufacturer) that is configured in config.properties. "
    		+ "This host information is also what identifies Photonic 3D printers through multicast to DLNA and UPNP clients on the user's network.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = SwaggerMetadata.SUCCESS),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
    @PUT
	@Path("hostInformation")
	public void setHostInformation(HostInformation info) {
		HostProperties.Instance().saveHostInformation(info);
	}
    
    @ApiOperation(value="This method returns all of the GUI skins available on the machine")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = SwaggerMetadata.SUCCESS),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
    @GET
	@Path("skins/list")
	public List<Skin> getSkins() {
		return HostProperties.Instance().getSkins();
	}
    
/*    @ApiOperation(value="This method activates one of the GUI skins available on the machine")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = SwaggerMetadata.SUCCESS),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
    @PUT
	@Path("skins/activate/{skinName}")
	public void activateSkin(@WebParam(name="skinName") String skinName) {
    	List<Skin> skins = HostProperties.Instance().getSkins();
    	for (Skin skin : skins) {
    		if (skin.getName().equals(skinName)) {
    			skin.setActive(true);
    		}
    	}
    	
		HostProperties.Instance().saveSkins(skins);
	}
    
    @ApiOperation(value="This method deactivates one of the GUI skins available on the machine")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = SwaggerMetadata.SUCCESS),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
    @PUT
	@Path("skins/deactivate/{skinName}")
	public void deactivateSkin(@WebParam(name="skinName") String skinName) {
    	List<Skin> skins = HostProperties.Instance().getSkins();
    	for (Skin skin : skins) {
    		if (skin.getName().equals(skinName)) {
    			skin.setActive(false);
    		}
    	}
    	
		HostProperties.Instance().saveSkins(skins);
	}*/
    
    @ApiOperation(value="This method permenently deletes a skin available on the machine")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = SwaggerMetadata.SUCCESS),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
    @DELETE
	@Path("skins/{skinName}")
	public void deleteSkin(@WebParam(name="skinName") String skinName) {
    	List<Skin> skins = HostProperties.Instance().getSkins();
    	Iterator<Skin> skinIter = skins.iterator();
    	for (Skin currentSkin = skinIter.next(); skinIter.hasNext();) {
    		if (currentSkin.getName().equals(skinName)) {
    			skinIter.remove();
    		}
    	}
    	
		HostProperties.Instance().saveSkins(skins);
	}
    
    @ApiOperation(value="This method allows the GUI to create a skin from scratch")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = SwaggerMetadata.SUCCESS),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
    @PUT
	@Path("skins")
	public void upsertSkin(Skin skin) {
    	List<Skin> skins = HostProperties.Instance().getSkins();
    	boolean skinFound = false;
    	for (int t = 0; t < skins.size(); t++) {
    		if (skins.get(t).getName().equals(skin.getName())) {
    			skins.set(t, skin);
    			skinFound = true;
    		}
    	}
    	if (!skinFound) {
    		skins.add(skin);
    	}
		HostProperties.Instance().saveSkins(skins);
	}
}