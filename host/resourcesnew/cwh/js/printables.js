(function() {
	var cwhApp = angular.module('cwhApp');
	cwhApp.controller("PrintablesController", ['$scope', '$http', '$location', '$uibModal', '$anchorScroll', '$window', 'cwhWebSocket', 'photonicUtils', function ($scope, $http, $location, $uibModal, $anchorScroll, $window, cwhWebSocket, photonicUtils) {
		controller = this;
	
		this.currentPrintable = null;
		this.currentCustomizer = null;
		this.currentPrinter = null;
		this.supportedFileTypes = null;
		this.currentPreviewImg = null;
		this.errorMsg = null;
		this.projectImage = "fa-sun-o";
		
		this.handlePreviewError = function handlePreviewError() {
			var printableName = encodeURIComponent(controller.currentPrintable.name);
			var printableExtension = encodeURIComponent(controller.currentPrintable.extension);
			$http.get("/services/customizers/renderPreviewImage/" + controller.currentCustomizer.name + "?_=" + controller.currentCustomizer.cacheId).success(
				function (data) {
					controller.errorMsg = null;
				}).error(
				function (data, status, headers, config, statusText) {
					controller.errorMsg = data;
				});
		}

		this.saveCustomizer = function saveCustomizer() {
			if (controller.currentPrintable != null && controller.currentCustomizer != null) {
				controller.currentCustomizer.externalImageAffectingState = photonicUtils.previewExternalStateId;
				$http.post("/services/customizers/upsert", controller.currentCustomizer).success(function (data) {
						controller.currentPreviewImg = "/services/customizers/renderPreviewImage/" + controller.currentCustomizer.name + "?_=" + data.cacheId;
					}).error(function (data, status, headers, config, statusText) {
 	        			$scope.$emit("HTTPError", {status:status, statusText:data});
	        		});
			}
		};

		this.changeCurrentPrintable = function changeCurrentPrintable(newPrintable) {
			var currentPrinterName = controller.currentPrinter != null?controller.currentPrinter.configuration.name:null;
			var newCustomizerName = newPrintable.name + "." + newPrintable.extension + (currentPrinterName != null?"." + currentPrinterName: "");
			if (controller.currentPrintable != null && newPrintable.name == controller.currentPrintable.name && newPrintable.extension == controller.currentPrintable.extension) {
				return;
			}
			
			controller.currentPrintable = newPrintable;
			controller.errorMsg = null;
			
			if (currentPrinterName == null) {
				controller.refreshCurrentPrinter();
				return;
			}
			
			$http.get("services/customizers/get/" + newCustomizerName + "?externalState=" + photonicUtils.previewExternalStateId).success(
					function (data) {
						if (data == "") {
							controller.currentCustomizer = {
									name: newCustomizerName,
									printerName: currentPrinterName,
									printableName: newPrintable.name,
									printableExtension: newPrintable.extension,
									supportsAffineTransformSettings: true,
									externalImageAffectingState:photonicUtils.previewExternalStateId,
									zscale: 1,
									nextSlice: 0,
									nextStep: "PerformHeader",
									imageManipulationCalculator: null,
									affineTransformSettings: {
										yflip: false,
										xflip: false,
										xscale: 1,
										yscale: 1,
										rotation:0,
										xtranslate: 0,
										ytranslate: 0,
										xshear: 0,
										yshear: 0,
										affineTransformScriptCalculator: null
									}
								};
						} else {
							controller.currentCustomizer = data;
							controller.currentCustomizer.externalImageAffectingState = photonicUtils.previewExternalStateId;
						}
						
						//We probably don't need to save the customizer here but we do it in case the externalState changed
						controller.saveCustomizer();
					});
		};

		this.refreshPrintables = function refreshPrintables() {
	  		$http.get("services/printables/list").success(
	  			function (data) {
	  				controller.printables = data;
	  			})
		};
		
		this.refreshCurrentPrinter = function refreshCurrentPrinter() {
			$http.get("services/printers/getFirstAvailablePrinter").success(
	  			function (data) {
	  				controller.currentPrinter = data;
	  			}).error(
	  			function (data) {
	  				controller.errorMsg = data;
	  			});
		};

		this.hostSocket = cwhWebSocket.connect("services/hostNotification", $scope).onJsonContent(
			function(data) {
				if (data.notificationEvent == "FileUploadComplete") {
					controller.refreshPrintables();
				}
			}
			// potentially add one for when customizerischanged to reset the preview again (this is the whole preview/customizer thing goes into a separate controller or somethng like that)
		);
		if (this.hostSocket === null) {
			$scope.$emit("MachineResponse",  {machineResponse: {command:"Browser Too Old", message:"You will need to use a modern browser to run this application."}});
		}

		this.printWithCustomizer = function printWithCustomizer() {
			var customizerName = encodeURIComponent(controller.currentCustomizer.name);
	        $http.post("/services/printers/startJob/" + customizerName).success(
	        		function (data) {
	        	        $location.path("/printJobsPage")
	        		}).error(
    				function (data, status, headers, config, statusText) {
 	        			$scope.$emit("HTTPError", {status:status, statusText:data});
	        		})
		}
		
		this.changeScale = function changeScale(x, y, z) {	
			controller.currentCustomizer.affineTransformSettings.xscale += x;
			controller.currentCustomizer.affineTransformSettings.yscale += y;
			controller.currentCustomizer.zscale += z;
			if (controller.currentCustomizer.affineTransformSettings.xscale <= 0) {
				controller.currentCustomizer.affineTransformSettings.xscale = .01;
			}
			if (controller.currentCustomizer.affineTransformSettings.yscale <= 0) {
				controller.currentCustomizer.affineTransformSettings.yscale = .01;
			}
			if (controller.currentCustomizer.zscale <= 0) {
				controller.currentCustomizer.zscale = .01;
			}
			this.saveCustomizer();
		}
		
		this.changeShear = function changeShear(x, y) {	
			controller.currentCustomizer.affineTransformSettings.xshear += x;
			controller.currentCustomizer.affineTransformSettings.yshear += y;
			this.saveCustomizer();
		}
		
		this.changeRotation = function changeRotation(rotation) {
			controller.currentCustomizer.affineTransformSettings.rotation += rotation;
			this.saveCustomizer();
		}
		
		this.setProjectImage = function setProjectImage() {
			var serviceCall = null;
			var oldValue = null;
			if (controller.projectImage != "fa-refresh fa-spin") {
				oldValue = controller.projectImage;
				controller.projectImage = "fa-refresh fa-spin";
			}
			if (oldValue == "fa-sun-o") {
				serviceCall = "services/customizers/projectCustomizerOnPrinter/" + encodeURIComponent(controller.currentCustomizer.name);
			} else {
				if (controller.currentPrinter == null) {
					return;
				}
				
				serviceCall = "services/printers/showBlankScreen/" + encodeURIComponent(controller.currentPrinter.configuration.name);
			}
			
			$http.get(serviceCall).success(function (data) {
	        	if (data.command == "blankscreenshown") {
					controller.projectImage = "fa-sun-o";
	        	} else {
	        		controller.projectImage = "fa-certificate";
	        	}
	        }).error(function (data, status, headers, config, statusText) {
	        	if (oldValue != null) {
	        		controller.projectImage = oldValue;
	        	}
			});
		}

		this.resetTranslation = function resetTranslation() {
			controller.currentCustomizer.zscale = 1.0;
			controller.currentCustomizer.nextSlice = 0;
			controller.currentCustomizer.nextStep = "PerformHeader";
			controller.currentCustomizer.imageManipulationCalculator = null;
			
			var affineTransformSettings = controller.currentCustomizer.affineTransformSettings;
			affineTransformSettings.xtranslate = 0;
			affineTransformSettings.ytranslate = 0;
			affineTransformSettings.xscale = 1.0;
			affineTransformSettings.yscale = 1.0;
			affineTransformSettings.xshear = 0.0;
			affineTransformSettings.yshear = 0.0;
			affineTransformSettings.xflip = false;
			affineTransformSettings.yflip = false;
			affineTransformSettings.rotation = 0;
			affineTransformSettings.affineTransformScriptCalculator = null;
			this.saveCustomizer();
		}

		this.goToSlacer = function goToSlacer() {
			window.open("/slacer", "slacer");
		}

		this.changeTranslate = function changeTranslate(x, y) {
			var affineTransformSettings = controller.currentCustomizer.affineTransformSettings;

			affineTransformSettings.xtranslate += x;
			affineTransformSettings.ytranslate += y;
			this.saveCustomizer();
		}

		this.printPrintable = function printPrintable() {
			var printableName = encodeURIComponent(controller.currentPrintable.name + "." + controller.currentPrintable.extension);
	        $http.post("/services/printables/print/" + printableName).success(
	        		function (data) {
	        			$location.path("/printJobsPage");
	        		}).error(
    				function (data, status, headers, config, statusText) {
 	        			$scope.$emit("HTTPError", {status:status, statusText:data});
	        		})
		};
		
		this.deletePrintable = function deletePrintable() {
			var printableName = encodeURIComponent(controller.currentPrintable.name);
			var printableExtension = encodeURIComponent(controller.currentPrintable.extension);
			var fileName = printableName + "." + printableExtension;
			var customizerName = encodeURIComponent(controller.currentPrintable.name);
	        $http.delete("/services/printables/delete/" + fileName).success(function (data) {
	        			$http.delete("services/customizers/delete/" + customizerName).success(function (data) {			
	        				    controller.refreshPrintables();
	        					controller.currentPrintable = null;
	        					controller.currentCustomizer = null;
        				}).error(function (data, status, headers, config, statusText) {
        					$scope.$emit("HTTPError", {status:status, statusText:data});
        				})
    		}).error(function (data, status, headers, config, statusText) {
    			$scope.$emit("HTTPError", {status:status, statusText:data});
    		})
	    };
	    
		$scope.downloadPrintable = function downloadPrintable(printable) {
        	$window.location.href = "services/printables/downloadPrintableFile/" + encodeURIComponent(printable.name + "." + printable.extension);
        }

		//TODO: When we get an upload complete message, we need to refresh file list...
		this.showUpload = function showUpload() {
			var fileChosenModal = $uibModal.open({
		        animation: true,
		        templateUrl: 'upload.html',
		        controller: 'UploadFileController',
		        size: "lg",
		        resolve: {
		        	title: function () {return "Upload Printable";},
		        	supportedFileTypes: function () {return null},
		        	getRestfulFileUploadURL: function () {return function (filename) {return '/services/printables/uploadPrintableFile/' + encodeURIComponent(filename);}},
		        	getRestfulURLUploadURL: function () {return function (filename, url) {return "services/printables/uploadviaurl/" + encodeURIComponent(filename) + "/" + encodeURIComponent(url);}}
		        }
			});
			
			fileChosenModal.result.then(function (uploadedPrintable) {this.refreshPrintables()});
		};
	  	
		this.clearExternalCacheAndSaveCustomizer = function clearExternalCacheAndSaveCustomizer() {
			photonicUtils.clearPreviewExternalState();
			controller.saveCustomizer();
		}
		
		this.testScript = function testScript(scriptName, returnType, script) {
			photonicUtils.testScript(controller, scriptName, returnType, script, controller.clearExternalCacheAndSaveCustomizer);
		};
		
		this.writeDrainHoldCode = function writeDrainHoldCode() {
			controller.currentCustomizer.imageManipulationCalculator = 
			"var drainHoleInMM = {\n" +
			"  centerX:centerX,\n" +
			"  centerY:centerY,\n" +
			"  widthX:5,\n" +
			"  widthY:5,\n" +
			"  depth:5};\n\n" +
			"if (($CURSLICE * $LayerThickness) <= drainHoleInMM.depth) {\n" +
			"   buildPlatformGraphics.setColor(java.awt.Color.BLACK);\n" + 
			"   buildPlatformGraphics.fillOval(\n" +
			"      (drainHoleInMM.centerX - (drainHoleInMM.widthX/2 * pixelsPerMMX)),\n" +
			"      (drainHoleInMM.centerY - (drainHoleInMM.widthY/2 * pixelsPerMMY)),\n" +
			"      drainHoleInMM.widthX * pixelsPerMMX,\n" +
			"      drainHoleInMM.widthY * pixelsPerMMY);\n" +
			"}\n";
			
			controller.clearExternalCacheAndSaveCustomizer();
		};
		
		this.writeCenteredTextCode = function writeCenteredTextCode() {
			controller.currentCustomizer.imageManipulationCalculator = 
				"var serial = \"123456\";\n" +
				"var twoDFont = job.buildFont();\n" +
				"var metrics = buildPlatformGraphics.getFontMetrics(twoDFont);\n" +
				"buildPlatformGraphics.setFont(twoDFont);\n" +
				"buildPlatformGraphics.setColor(java.awt.Color.WHITE);\n" +
				"buildPlatformGraphics.drawString(serial, centerX - metrics.stringWidth(serial) / 2, centerY - metrics.getHeight() / 2 + metrics.getAscent());\n"
				
				controller.clearExternalCacheAndSaveCustomizer();
		};
			
		this.writeDuplicationGridCode = function writeDuplicationGridCode() {
			controller.currentCustomizer.imageManipulationCalculator = 
				"var gridDataInMM = {\n" +
				"  distanceBetweenImagesX: 1,\n" +
				"  distanceBetweenImagesY: 1,\n" +
				"  numberOfRows:3,\n" +
				"  numberOfColumns:3};\n\n" +
				"for (var x = 0; x < gridDataInMM.numberOfColumns; x++) {\n" +
				"   for (var y = 0; y < gridDataInMM.numberOfRows; y++) {\n" +
				"      if (x > 0 || y > 0) {\n" +
				"         var currentTransform = new java.awt.geom.AffineTransform(affineTransform);\n" +
				"         currentTransform.translate(\n" +
				"            Math.round(x * gridDataInMM.distanceBetweenImagesX * pixelsPerMMX) + (x * printableShape.getWidth()),\n" +
				"            Math.round(y * gridDataInMM.distanceBetweenImagesY * pixelsPerMMY) + (y * printableShape.getHeight()));\n" +
				"         buildPlatformGraphics.drawImage(printImage, currentTransform, null);\n" +
				"      }\n" +
				"   }\n" +
				"}\n";
				controller.clearExternalCacheAndSaveCustomizer();
		};
		
		this.writeDuplicationGridWithVariableExposureTimeCode = function writeDuplicationGridWithVariableExposureTimeCode() {
			controller.currentCustomizer.imageManipulationCalculator = 
				"var gridDataInMM = {\n" +
				"  distanceBetweenImagesX: 1,\n" +
				"  distanceBetweenImagesY: 1,\n" +
				"  numberOfRows:3,\n" +
				"  numberOfColumns:3,\n" +
				"  exposureTimeDecrementMillis:1000};\n\n" +
				"for (var x = 0; x < gridDataInMM.numberOfColumns; x++) {\n" +
				"   for (var y = 0; y < gridDataInMM.numberOfRows; y++) {\n" +
				"      if (x > 0 || y > 0) {\n" +
				"         var currentTransform = new java.awt.geom.AffineTransform(affineTransform);\n" +
				"         currentTransform.translate(\n" +
				"            Math.round(x * gridDataInMM.distanceBetweenImagesX * pixelsPerMMX) + (x * printableShape.getWidth()),\n" +
				"            Math.round(y * gridDataInMM.distanceBetweenImagesY * pixelsPerMMY) + (y * printableShape.getHeight()));\n" +
				"         buildPlatformGraphics.drawImage(printImage, currentTransform, null);\n" +
				"         exposureTimers.add({\n" +
				"            delayMillis:$LayerTime - ((y * gridDataInMM.numberOfColumns) + x) * gridDataInMM.exposureTimeDecrementMillis,\n" + 
				"            parameter:currentTransform.createTransformedShape(printableShape),\n" +
                "            function:function(blackRect) {\n" +
                "               buildPlatformGraphics.setColor(java.awt.Color.BLACK);\n" +
                "               buildPlatformGraphics.fill(blackRect);\n" + 
				"            }\n" + 
				"         });\n" +
				"      }\n" +
				"   }\n" +
				"}\n";
				controller.clearExternalCacheAndSaveCustomizer();
		};
		
		this.writeDuplicationGridWithSerialNumber = function() {
			controller.currentCustomizer.imageManipulationCalculator = 
			        "var gridDataInMM = {\n" + 
					"  distanceBetweenImagesX: 1,\n" + 
					"  distanceBetweenImagesY: 1,\n" + 
					"  numberOfRows:3,\n" + 
					"  numberOfColumns:3,\n" + 
					"  startingSerialNumber:888,\n" + 
					"  serialNumberCenterXPixels: printableShape.getWidth() / 2,\n" + 
					"  serialNumberCenterYPixels: printableShape.getHeight() / 2,\n" + 
					"  serialNumberDepth:0.5};\n\n" + 
					"var twoDFont = job.buildFont();\n" + 
					"var metrics = buildPlatformGraphics.getFontMetrics(twoDFont);\n" + 
					"var oldTransform = buildPlatformGraphics.getTransform();\n" + 
					"buildPlatformGraphics.setFont(twoDFont);\n" + 
					"buildPlatformGraphics.setColor(java.awt.Color.BLACK);\n" + 
					"for (var x = 0; x < gridDataInMM.numberOfColumns; x++) {\n" + 
					"   for (var y = 0; y < gridDataInMM.numberOfRows; y++) {\n" + 
					"      var currentTransform = new java.awt.geom.AffineTransform(affineTransform);\n" + 
					"      if (x > 0 || y > 0) {\n" + 
					"         currentTransform.translate(\n" + 
					"            Math.round(x * gridDataInMM.distanceBetweenImagesX * pixelsPerMMX) + (x * printableShape.getWidth()),\n" + 
					"            Math.round(y * gridDataInMM.distanceBetweenImagesY * pixelsPerMMY) + (y * printableShape.getHeight()));\n" + 
					"         buildPlatformGraphics.drawImage(printImage, currentTransform, null);\n" + 
					"      }\n" + 
					"      if (($CURSLICE * $LayerThickness) <= gridDataInMM.serialNumberDepth) {\n" + 
					"         buildPlatformGraphics.setTransform(currentTransform);\n" +
					"         var nextSerialNumber = new java.lang.Integer(gridDataInMM.startingSerialNumber + (x * gridDataInMM.numberOfRows) + y).toString(16).toUpperCase();\n" + 
					"         buildPlatformGraphics.drawString(nextSerialNumber, \n" + 
					"            gridDataInMM.serialNumberCenterXPixels - metrics.stringWidth(nextSerialNumber) / 2, \n" + 
					"            gridDataInMM.serialNumberCenterYPixels - metrics.getHeight() / 2 + metrics.getAscent());\n" + 
					"         buildPlatformGraphics.setTransform(oldTransform);\n" + 
					"      }\n" + 
					"   }\n" + 
					"}\n";
					controller.clearExternalCacheAndSaveCustomizer();
		}
		
		this.write3dTwistCode = function write3dTwistCode() {
			controller.currentCustomizer.affineTransformSettings.affineTransformScriptCalculator = 
				"var currentTransform = new java.awt.geom.AffineTransform();\n" +
				"currentTransform.rotate(java.lang.Math.toRadians($CURSLICE));\n" +
				"currentTransform.translate(\n" +
				"   centerX-printImage.getWidth()/2,\n" +
				"   centerY-printImage.getHeight()/2);\n" +
				"currentTransform";
		}
		
		this.correctAspectRatio = function correctAspectRatio() {
			controller.currentCustomizer.affineTransformSettings.affineTransformScriptCalculator = 
				"var currentTransform = new java.awt.geom.AffineTransform();\n" +
				"var scaleXDimension = false;\n" +
				"var ppmmx = pixelsPerMMX;\n" +
				"var ppmmy = pixelsPerMMY;\n" +
				"function reduce(numerator,denominator){\n" +
				"   var gcd = function gcd(a,b){\n" +
				"      return b ? gcd(b, a%b) : a;\n" +
				"   };\n" +
				"   gcd = gcd(numerator,denominator);\n" +
				"   return [numerator/gcd, denominator/gcd];\n" +
				"}\n" +
				"var reduced = reduce(ppmmx, ppmmy);" +
				"ppmmx = reduced[0];\n" +
				"ppmmy = reduced[1];\n" +
				"if (scaleXDimension) {\n" +
				"   currentTransform.scale(ppmmx / ppmmy, 1);\n" +
				"} else {\n" +
				"   currentTransform.scale(1, ppmmy / ppmmx);\n" +
				"}\n" +
				"currentTransform";
		}
		
		this.getPrintableIconClass = function getPrintableIconClass(printable) {
			return photonicUtils.getPrintFileProcessorIconClass(printable);
		};

		this.refreshPrintables();
		this.refreshCurrentPrinter();
	}]);

	

	cwhApp.directive('handleError', function() {
			return {
				link: function(scope, element, attrs) {
					
					var pc = scope.printablesController;
					element.bind('error', function() {
						pc.handlePreviewError();
						scope.$apply();
					});

				}
			};
		});
})();
