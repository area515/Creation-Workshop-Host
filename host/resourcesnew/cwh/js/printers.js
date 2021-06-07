(function() {
	var cwhApp = angular.module('cwhApp');
	cwhApp.controller("PrintersController", ['$scope', '$http', '$location', '$anchorScroll', '$uibModal', 'photonicUtils', function ($scope, $http, $location, $anchorScroll, $uibModal, photonicUtils) {
		controller = this;
		var PRINTERS_DIRECTORY = "printers";
		var BRANCH = "master";
		var REPO = $scope.repo;
		
		var tempSLicingProfile;
		
		this.loadingFontsMessage = "--- Loading fonts from server ---";
		this.loadingProfilesMessage = "--- Loading slicing profiles from server ---";
		this.loadingPrinterDriversMessage = "--- Loading printer drivers from server ---";
		this.loadingMachineConfigMessage = "--- Loading machine configurations from server ---";
		this.autodirect = $location.search().autodirect;
		
		function findAPrinterThatTheUserMostLikelyWantsToWorkWith(printerList) {
			//There is only one printer. So it's likely they want to work with this printer
			if (printerList.length == 1) {
				return printerList[0];
			}
			
			var firstStartedPrinter = null;
        	for (var i = 0; i < printerList.length; i++) {
        		//If the user has already selected a printer. It's very likely that they want to work with it...
        		if (controller.currentPrinter != null && printerList[i].configuration.name === controller.currentPrinter.configuration.name) {
        			return printerList[i];
        		}

        		if (firstStartedPrinter == null && printerList[i].started) {
        			firstStartedPrinter = printerList[i];
        		}
        		
        		//TODO: Isn't it more likely that they want to work with a printer that is printing than one that is simply just started?
        	}
        	
        	//As the name implies, this will return the first started printer. There is a decent chance they want to work with it.
        	return firstStartedPrinter;
		}
		
		//TODO: Instead of having this method we should understand how the selected printer gets out of sync and fix that
		function refreshSelectedPrinterAndAutodirectIfNecessary(printerList) {
			controller.currentPrinter = findAPrinterThatTheUserMostLikelyWantsToWorkWith(printerList);
			if (controller.autodirect != 'disabled') {
				controller.gotoPrinterControls();
			}
        }
		
		function refreshPrinters() {
	        $http.get('/services/printers/list').success(function(data) {
	        	$scope.printers = data;
	        	refreshSelectedPrinterAndAutodirectIfNecessary(data);
	        });
	    }
		
		function executeActionAndRefreshPrinters(command, message, service, targetPrinter, postTargetPrinter, shouldRefreshPrinterList) {
			if (targetPrinter == null) {
    			$scope.$emit("MachineResponse", {machineResponse: {command:command, message:message, successFunction:null, afterErrorFunction:null}});
		        return;
			}
			var printerName = encodeURIComponent(targetPrinter.configuration.name);
			if (postTargetPrinter) {
			   $http.post(service, targetPrinter).then(
	       			function(response) {
	       				if (!shouldRefreshPrinterList) {
	       					controller.currentPrinter = targetPrinter;
	       				}
       					refreshPrinters();
       					refreshSlicingProfiles();
       					refreshMachineConfigurations();
	       			}, 
	       			function(response) {
 	        			$scope.$emit("HTTPError", {status:response.status, statusText:response.data});
	       		}).then(function() {
	       			    $('#start-btn').attr('class', 'fa fa-play');
	        			$('#stop-btn').attr('class', 'fa fa-stop');
	       		});
		    } else {
		       $http.get(service + printerName).then(
		       		function(response) {
		        		$scope.$emit("MachineResponse", {machineResponse: response.data, successFunction:shouldRefreshPrinterList?refreshPrinters:null, afterErrorFunction:null});
		       		}, 
		       		function(response) {
	 	        		$scope.$emit("HTTPError", {status:response.status, statusText:response.data});
		       		}).then(function() {
		       			    $('#start-btn').attr('class', 'fa fa-play');
		        			$('#stop-btn').attr('class', 'fa fa-stop');
		       		});
			}
		}
		
		$scope.editCurrentPrinter = function editCurrentPrinter(editTitle) {
			controller.editTitle = editTitle;
			controller.editPrinter = JSON.parse(JSON.stringify(controller.currentPrinter));
			openSavePrinterDialog(editTitle, false);
		}

		$scope.savePrinter = function savePrinter(printer, isNewPrinter) {
			if (isNewPrinter) {//Rename the profiles to what the user entered if this is a new printer
				controller.editPrinter.configuration.MachineConfigurationName = controller.editPrinter.configuration.name;
				controller.editPrinter.configuration.SlicingProfileName = controller.editPrinter.configuration.name;
			}
			executeActionAndRefreshPrinters("Save Printer", "No printer selected to save.", '/services/printers/save', printer, true, isNewPrinter);
	        controller.editPrinter = null;
	        controller.openType = null;
	        photonicUtils.clearPreviewExternalState();
		}
		
		function openSavePrinterDialog(editTitle, isNewPrinter) {
			var editPrinterModal = $uibModal.open({
		        animation: true,
		        templateUrl: 'editPrinter.html',
		        controller: 'EditPrinterController',
		        size: "lg",
		        resolve: {
		        	title: function () {return editTitle;},
		        	editPrinter: function () {return controller.editPrinter;}
		        }
			});
		    editPrinterModal.result.then(function (savedPrinter) {$scope.savePrinter(savedPrinter, isNewPrinter)});
		}
		
		//TODO: When we get an upload complete message, we need to refresh file list...
		$scope.showFontUpload = function showFontUpload() {
			var fileChosenModal = $uibModal.open({
		        animation: true,
		        templateUrl: 'upload.html',
		        controller: 'UploadFileController',
		        size: "lg",
		        resolve: {
		        	title: function () {return "Upload True Type Font";},
		        	supportedFileTypes: function () {return ".ttf";},
		        	getRestfulFileUploadURL: function () {return function (filename) {return '/services/machine/uploadFont';}},
		        	getRestfulURLUploadURL: function () {return null;}
		        }
			});
			
			//fileChosenModal.result.then(function (savedPrinter) {$scope.savePrinter(savedPrinter, newPrinter)});
		}
		
		$scope.installCommunityPrinter = function installCommunityPrinter(printer) {
	        $http.get(printer.url).success(
	        		function (data) {
	        			controller.editPrinter = JSON.parse(window.atob(data.content));
	        			$scope.savePrinter(controller.editPrinter, true);
	        		}).error(
    				function (data, status, headers, config, statusText) {
 	        			$scope.$emit("HTTPError", {status:status, statusText:data});
	        		})
	        return;
	    }
		
		this.createNewPrinter = function createNewPrinter(editTitle) {
			if (controller.currentPrinter == null) {
		        $http.post('/services/printers/createTemplatePrinter').success(
		        		function (data) {
		        			controller.editPrinter = data;
		        			openSavePrinterDialog(editTitle, true);
		        		}).error(
	    				function (data, status, headers, config, statusText) {
	 	        			$scope.$emit("HTTPError", {status:status, statusText:data});
		        		})
		        return;
			}
			
			controller.editPrinter = JSON.parse(JSON.stringify(controller.currentPrinter));
			controller.editPrinter.configuration.name = controller.editPrinter.configuration.name + " (Copy)";
			//These must be set before we save a printer, otherwise the xml files aren't saved properly
			controller.editPrinter.configuration.MachineConfigurationName = controller.editPrinter.configuration.name;
			controller.editPrinter.configuration.SlicingProfileName = controller.editPrinter.configuration.name;
			openSavePrinterDialog(editTitle, true);
		}

		this.writePegExposureCode = function writePegExposureCode() {
			controller.currentPrinter.configuration.slicingProfile.TwoDimensionalSettings.PlatformCalculator = 
				    "var pegSettingsMM = {\n" +
					"  rows: 5,\n" +
					"  columns: 5,\n" +
					"  fontDepth: .5,\n" +
					"  fontPointSize: 42,\n" +
					"  startingOverhangDegrees: 45,\n" +
					"  degreeIncrement: 5,\n" +
					"  pegDiameter: 3,\n" +
					"  pegStandHeight: 1,\n" +
					"  pegStandWidth: 5,\n" +
					"  distanceBetweenStands: 1,\n" +
					"  exposureTimeDecrementMillis: 1000};\n\n" +
					"var pegStandCount = pegSettingsMM.pegStandHeight / $LayerThickness;\n" +
					"var fontCount = pegSettingsMM.fontDepth / $LayerThickness;\n" +
					"var pegSettingsPixels = {\n" +
					"  pegDiameterX: pegSettingsMM.pegDiameter * pixelsPerMMX,\n" +
					"  pegDiameterY: pegSettingsMM.pegDiameter * pixelsPerMMY,\n" +
					"  pegStandWidthX: pegSettingsMM.pegStandWidth * pixelsPerMMX,\n" +
					"  pegStandWidthY: pegSettingsMM.pegStandWidth * pixelsPerMMY,\n" +
					"  distanceBetweenStandsX: pegSettingsMM.distanceBetweenStands * pixelsPerMMX,\n" +
					"  distanceBetweenStandsY: pegSettingsMM.distanceBetweenStands * pixelsPerMMY,\n" +
					"  pegStandDifferenceOffsetX: ((pegSettingsMM.pegStandWidth * pixelsPerMMX) - (pegSettingsMM.pegDiameter * pixelsPerMMX)) / 2,\n" +
					"  pegStandDifferenceOffsetY: ((pegSettingsMM.pegStandWidth * pixelsPerMMY) - (pegSettingsMM.pegDiameter * pixelsPerMMY)) / 2\n" +
					"}\n" +
					"if ($CURSLICE < pegStandCount) {\n" +
					"   for (var x = 0; x < pegSettingsMM.columns; x++) {\n" +
					"      for (var y = 0; y < pegSettingsMM.rows; y++) {\n" +
					"         var overhangAngle = pegSettingsMM.startingOverhangDegrees + y * pegSettingsMM.degreeIncrement;\n" +
					"         var startingX = x * pegSettingsPixels.pegStandWidthX + x * pegSettingsPixels.distanceBetweenStandsX;\n" +
					"         var startingY = y * pegSettingsPixels.pegStandWidthY + y * pegSettingsPixels.distanceBetweenStandsY;\n" +
					"         buildPlatformGraphics.setColor(java.awt.Color.WHITE);\n" +
					"         buildPlatformGraphics.fillRect(\n" +
					"            startingX,\n" +
					"            startingY,\n" +
					"            pegSettingsPixels.pegStandWidthX,\n" +
					"            pegSettingsPixels.pegStandWidthY);\n" +
					"         if ($CURSLICE < fontCount) {\n" +
					"            buildPlatformGraphics.setColor(java.awt.Color.BLACK);\n" +
					"            buildPlatformGraphics.setFont(new java.awt.Font(\"Dialog\", 0, pegSettingsMM.fontPointSize));\n" +
					"            buildPlatformGraphics.drawString(overhangAngle + \"\", startingX, startingY + pegSettingsPixels.pegStandWidthY);\n" +
					"         }\n" +
					"         exposureTimers.add({\n" +
					"             delayMillis:$LayerTime - (pegSettingsMM.exposureTimeDecrementMillis * x),\n" + 
					"             parameter:{x:startingX, y:startingY, width:pegSettingsPixels.pegStandWidthX, height:pegSettingsPixels.pegStandWidthY},\n" +
					"             function:function(blackRect) {\n" +
					"                buildPlatformGraphics.setColor(java.awt.Color.BLACK);\n" +
					"                buildPlatformGraphics.fillRect(\n" +
					"                   blackRect.x,\n" +
					"                   blackRect.y,\n" +
					"                   blackRect.width,\n" +
					"                   blackRect.height);\n" +
					"             }\n" +
					"         });\n" +
					"      }\n" +
					"   }\n" +
					"} else {\n" +
					"   for (var x = 0; x < pegSettingsMM.columns; x++) {\n" +
					"      for (var y = 0; y < pegSettingsMM.rows; y++) {\n" +
					"         var overhangAngle = pegSettingsMM.startingOverhangDegrees + y * pegSettingsMM.degreeIncrement;\n" +
					"         var singleOverhangIncrement = java.lang.Math.tan(java.lang.Math.toRadians(overhangAngle)) * $LayerThickness * pixelsPerMMX;\n" +
					"         var circleOffsetX = pegSettingsPixels.pegStandDifferenceOffsetX * ((x + 1) * 2 - 1) + (singleOverhangIncrement * ($CURSLICE - pegStandCount)) + (x * pegSettingsPixels.pegDiameterX) + (x * pegSettingsPixels.distanceBetweenStandsX);\n" +
					"         var circleOffsetY = pegSettingsPixels.pegStandDifferenceOffsetY * ((y + 1) * 2 - 1) + (y * pegSettingsPixels.pegDiameterY) + (y * pegSettingsPixels.distanceBetweenStandsY);\n" +
					"         buildPlatformGraphics.fillOval(\n" +
					"            circleOffsetX,\n" +
					"            circleOffsetY,\n" +
					"            pegSettingsPixels.pegDiameterX,\n" +
					"            pegSettingsPixels.pegDiameterY);\n" +
					"         exposureTimers.add({\n" +
					"             delayMillis:$LayerTime - (pegSettingsMM.exposureTimeDecrementMillis * x),\n" +
					"             parameter:{x:circleOffsetX, y:circleOffsetY, width:pegSettingsPixels.pegDiameterX, height:pegSettingsPixels.pegDiameterY},\n" +
					"             function:function(blackRect) {\n" +
					"                buildPlatformGraphics.setColor(java.awt.Color.BLACK);\n" +
					"                buildPlatformGraphics.fillOval(\n" +
					"                   blackRect.x,\n" +
					"                   blackRect.y,\n" +
					"                   blackRect.width,\n" +
					"                   blackRect.height);\n" +
					"             }\n" +
					"         });\n" +
					"      }\n" +
					"   }\n" +
					"}\n";
		}
		
		this.writeHBridgeCode = function writeHBridgeCode() {
			controller.currentPrinter.configuration.slicingProfile.TwoDimensionalSettings.PlatformCalculator = 
				        "var hBridgeInMM = {\n" +
						"   wallWidth:1,\n" +
						"   gapLength:4,\n" +
						"   firstGapWidth:3,\n" +
						"   numberOfGapsInRow:6,\n" +
						"   gapWidthIncrement:3,\n" +
						"   distanceBetweenRows:1,\n" +
						"   numberOfRows:5,\n" +
						"   exposureTimeDecrementMillis:1000\n" +
						"  };\n\n" +
						"var wallWidthX = hBridgeInMM.wallWidth * pixelsPerMMX;\n" +
						"var wallWidthY = hBridgeInMM.wallWidth * pixelsPerMMY;\n" +
						"var gapLengthY = hBridgeInMM.gapLength * pixelsPerMMY;\n" +
						"var lastTermOfSeries = hBridgeInMM.firstGapWidth + hBridgeInMM.gapWidthIncrement * (hBridgeInMM.numberOfGapsInRow - 1);\n" +
						"var totalWidthX = ((hBridgeInMM.wallWidth + hBridgeInMM.wallWidth * hBridgeInMM.numberOfGapsInRow) + (hBridgeInMM.numberOfGapsInRow * (hBridgeInMM.firstGapWidth + lastTermOfSeries) / 2)) * pixelsPerMMX;\n" +
						"var startX = centerX - totalWidthX / 2;\n" +
						"var startY = hBridgeInMM.numberOfRows * (hBridgeInMM.gapLength * 2 + hBridgeInMM.wallWidth) + (hBridgeInMM.numberOfRows - 1) * hBridgeInMM.distanceBetweenRows;\n" +
						"startY = centerY - startY * pixelsPerMMY / 2;\n" +
						"var currentY = startY;\n" +
						"buildPlatformGraphics.setColor(java.awt.Color.WHITE);\n" +
						"for (var currentRow = 0; currentRow < hBridgeInMM.numberOfRows; currentRow++) {\n" +
						"   var currentX = startX;\n" +
						"   if ($CURSLICE < job.totalSlices) {\n" +
						"      for (var currentGap = 0; currentGap < hBridgeInMM.numberOfGapsInRow; currentGap ++) {\n" +
						"         buildPlatformGraphics.fillRect(currentX, currentY, wallWidthX, gapLengthY * 2 + wallWidthY);\n" +
						"         currentX += wallWidthX + (hBridgeInMM.firstGapWidth + (hBridgeInMM.gapWidthIncrement * currentGap)) * pixelsPerMMX;\n" +
						"      }\n" +
						"      buildPlatformGraphics.fillRect(currentX, currentY, wallWidthX, gapLengthY * 2 + wallWidthY);\n" +
						"      buildPlatformGraphics.fillRect(startX, currentY + gapLengthY, totalWidthX, wallWidthY);\n" +
						"   } else {\n" +
						"      buildPlatformGraphics.fillRect(startX, currentY, totalWidthX, gapLengthY * 2 + wallWidthY);\n" +
						"      exposureTimers.add({\n" +
						"         delayMillis:$LayerTime - (hBridgeInMM.exposureTimeDecrementMillis * currentRow),\n" + 
						"         parameter:{x:startX, y:currentY, width:totalWidthX, height:gapLengthY * 2 + wallWidthY},\n" + 
						"         function:function(blackRect) {\n" +
						"            buildPlatformGraphics.setColor(java.awt.Color.BLACK);\n" +
						"            buildPlatformGraphics.fillRect(blackRect.x, blackRect.y, blackRect.width, blackRect.height);\n" +
						"         }\n" +
						"      });\n" +
						"   }\n" +
						"   currentY += gapLengthY * 2 + wallWidthY + hBridgeInMM.distanceBetweenRows * pixelsPerMMY;\n" +
						"}\n";
		}
		
		function createNewResinProfile(newResinProfile) {
			// this adds the new resinprofile in the current selected slicingprofile
			var newSlicingProfile = controller.currentPrinter.configuration.slicingProfile;
			newSlicingProfile.InkConfig.push(newResinProfile);
									
			// this re-uploads the changed profile
			$http.put("services/machine/slicingProfiles", newSlicingProfile).then(
		    		function (data) {
		    			// for some reason this is needed when it is the currently loaded profile, otherwise it won't show after refresh
				        $http.post('/services/printers/save', controller.currentPrinter).success(
				        		function () {
				        			refreshSlicingProfiles();
					    			$scope.$emit("MachineResponse", {machineResponse: {command:"Settings Saved!", message:"Your new resin profile has been added!.", response:true}, successFunction:null, afterErrorFunction:null});
				        		}).error(
			    				function (data, status, headers, config, statusText) {
			 	        			$scope.$emit("HTTPError", {status:status, statusText:data});
				        		})
		    		},
		    		function (error) {
 	        			$scope.$emit("HTTPError", {status:error.status, statusText:error.data});
		    		}
		    )	
		}
		
		this.copySlicingProfile = function copySlicingProfile(editTitle) {
			controller.currentSlicingProfile = JSON.parse(JSON.stringify(controller.currentPrinter.configuration.slicingProfile));
			controller.currentSlicingProfile.name = controller.currentSlicingProfile.name + " (Copy) ";
			openCopySlicingProfileDialog(controller.currentSlicingProfile, editTitle, controller.currentSlicingProfile.name);
		}
		
		function SaveEditSlicingProfile(savedProfile){
			$http.put("services/machine/slicingProfiles", savedProfile).then(
		    		function (data) {
		    			refreshSlicingProfiles();
		    			$scope.$emit("MachineResponse", {machineResponse: {command:"Settings Saved!", message:"Your slicing profile has been copied!.", response:true}, successFunction:null, afterErrorFunction:null});
		    		},
		    		function (error) {
 	        			$scope.$emit("HTTPError", {status:error.status, statusText:error.data});
		    		}
		    )
		}
		
		this.openSaveResinDialog = function openSaveResinDialog(editTitle) {
			var editPrinterModal = $uibModal.open({
		        animation: true,
		        templateUrl: 'editResin.html',
		        controller: 'EditResinController',
		        size: "lg",
		        resolve: {
		        	title: function () {return editTitle;},
		        	editPrinter: function () {return controller.editPrinter;}
		        }
			});
		    editPrinterModal.result.then(function (newResinProfile) {
		    	createNewResinProfile(newResinProfile)
			});
		}
		
		function openCopySlicingProfileDialog(data, editTitle, currentSlicingProfileName) {
			var copySlicingProfileModal = $uibModal.open({
		        animation: true,
		        templateUrl: 'copySlicingProfile.html',
		        controller: 'copySLicingProfileController',
		        size: "lg",
		        resolve: {
		        	title: function () {return editTitle;},
		        	sliceData: function () {return data;},
					nameProfile: function() {return currentSlicingProfileName;}
		        }
			});
		    copySlicingProfileModal.result.then(function (savedProfile) {
				SaveEditSlicingProfile(savedProfile);  
			});
		}

		this.deleteSlicingProfile = function deleteSlicingProfile(profileName, newProfile) {
			
			var profileNameEn = encodeURIComponent(profileName);
		     $http.delete("/services/machine/slicingProfiles/" + profileNameEn).success(function (data) {
		       	 refreshSlicingProfiles();
		    	 $scope.$emit("MachineResponse", {machineResponse: {command:"Settings removed!", message:"Your slicing profile has been removed succesfully!.", response:true}, successFunction:null, afterErrorFunction:null});							
		    	
		     }).error(
	    				function (data, status, headers, config, statusText) {
	 	        			$scope.$emit("HTTPError", {status:status, statusText:data});
		        		})
		}
		
		this.deleteCurrentResinProfile = function deleteCurrentResinProfile(slicingProfile) {
			// removes the selected resinprofile from the old profile
			slicingProfile.InkConfig.splice(slicingProfile.selectedInkConfigIndex,1);
			
			// this re-uploads the changed profile
			$http.put("services/machine/slicingProfiles", slicingProfile).then(
		    		function (data) {
		    			// for some reason this is needed when it is the currently loaded profile, otherwise it won't show after refresh
				        $http.post('/services/printers/save', controller.currentPrinter).success(
				        		function () {
				        			refreshSlicingProfiles();
					    			$scope.$emit("MachineResponse", {machineResponse: {command:"Settings Saved!", message:"Your resin profile has been removed!.", response:true}, successFunction:null, afterErrorFunction:null});
				        		}).error(
			    				function (data, status, headers, config, statusText) {
			 	        			$scope.$emit("HTTPError", {status:status, statusText:data});
				        		})
		    		},
		    		function (error) {
 	        			$scope.$emit("HTTPError", {status:error.status, statusText:error.data});
		    		}
		    )
		}
		
		this.startCurrentPrinter = function startCurrentPrinter() {
			$('#start-btn').attr('class', 'fa fa-refresh fa-spin');
			executeActionAndRefreshPrinters("Start Printer", "No printer selected to start.", '/services/printers/start/', controller.currentPrinter, false, true);
		}
		
		this.stopCurrentPrinter = function stopCurrentPrinter() {
			$('#stop-btn').attr('class', 'fa fa-refresh fa-spin');			
			executeActionAndRefreshPrinters("Stop Printer", "No printer selected to Stop.", '/services/printers/stop/', controller.currentPrinter, false, true);
		}
		
		this.deleteCurrentPrinter = function deleteCurrentPrinter() {
			executeActionAndRefreshPrinters("Delete Printer", "No printer selected to Delete.", '/services/printers/delete/', controller.currentPrinter, false, true);
	        controller.currentPrinter = null;
		}
		
		this.changeCurrentPrinter = function changeCurrentPrinter(newPrinter) {
			controller.currentPrinter = newPrinter;
		}
		
        this.gotoPrinterControls = function gotoPrinterControls() {
        	if (controller.currentPrinter == null) {
        		return;
        	}
        	$location.path('/printerControlsPage').search({printerName: controller.currentPrinter.configuration.name})
        };
		
		this.testTemplate = function testTemplate(scriptName, script) {
			var printerNameEn = encodeURIComponent(controller.currentPrinter.configuration.name);
			var scriptNameEn = encodeURIComponent(scriptName);
			
			$http.post('/services/printers/testTemplate/' + printerNameEn + "/" + scriptNameEn, script).success(function (data) {
				if (data.error) {
	     			$scope.$emit("MachineResponse", {machineResponse: {command:scriptName, message:data.errorDescription}, successFunction:null, afterErrorFunction:null});
				} else {
	     			$scope.$emit("MachineResponse", {machineResponse: {command:scriptName, message:"Successful execution. Template returned:" + data.result, response:true}, successFunction:null, afterErrorFunction:null});
				}
			}).error(function (data, status, headers, config, statusText) {
     			$scope.$emit("HTTPError", {status:status, statusText:data});
    		})
		}
		
		this.testRemainingPrintMaterial = function testRemainingPrintMaterial(printer) {
			var printerNameEn = encodeURIComponent(printer.configuration.name);
			
			$http.get('/services/printers/remainingPrintMaterial/' + printerNameEn).success(function (data) {
				//if (data.error) {
	     			$scope.$emit("MachineResponse", {machineResponse: data, successFunction:null, afterErrorFunction:null});
				/*} else {
	     			$scope.$emit("MachineResponse", {machineResponse: {command:scriptName, message:"Successful execution. Template returned:" + data.result, response:true}, successFunction:null, afterErrorFunction:null});
				}*/
			}).error(function (data, status, headers, config, statusText) {
     			$scope.$emit("HTTPError", {status:status, statusText:data});
    		})
		}
		
		$http.get('/services/machine/supportedFontNames').success(
				function (data) {
					controller.fontNames = data;
					controller.loadingFontsMessage = "Select a font...";
				});
		
		function refreshSlicingProfiles() {
			$http.get('/services/machine/slicingProfiles/list').success(
					function (data) {
						controller.slicingProfiles = data;
						controller.loadingProfilesMessage = "Select a slicing profile...";
					});
		}
		
		function refreshPrinterDrivers() {
			$http.get('/services/machine/printerDrivers/list').success(
					function (data) {
						controller.printerDrivers = data;
						controller.loadingPrinterDriversMessage = "Select a printer driver...";
					});
		}
		
		function refreshMachineConfigurations() {
			$http.get('/services/machine/machineConfigurations/list').success(
					function (data) {
						controller.machineConfigurations = data;
						controller.loadingMachineConfigMessage = "Select a machine configuration...";
					});
		}
		
		$http.get("https://api.github.com/repos/" + $scope.repo + "/contents/host/" + PRINTERS_DIRECTORY + "?ref=" + BRANCH).success(
			function (data) {
				$scope.communityPrinters = data;
			}
		);
		
		this.testScript = function testScript(scriptName, returnType, script) {
			photonicUtils.testScript(controller, scriptName, returnType, script);
		};
		
		controller.inkDetectors = [{name:"Visual Ink Detector", className:"org.area515.resinprinter.inkdetection.visual.VisualPrintMaterialDetector"},
		                           {name:"Digital GPIO Ink Detector", className:"org.area515.resinprinter.inkdetection.gpio.GpioDigitalPinInkDetector"}];
		$scope.ControlFlows = [	"Always",
		                        "OnSuccess",
		                        "OnSuccessAndCancellation"];
		refreshSlicingProfiles();
		refreshMachineConfigurations();
		refreshPrinters();
		refreshPrinterDrivers();
	}])

})();
