(function() {
	var cwhApp = angular.module('cwhApp');
	cwhApp.controller("SettingsController", ['$scope', '$http', '$location', '$routeParams', '$interval', 'cwhWebSocket', function ($scope, $http, $location, $routeParams, $interval, cwhWebSocket) {
		controller = this;
		var timeoutValue = 500;
		var maxUnmatchedPings = 3;//Maximum number of pings before we assume that we lost our connection
		var unmatchedPing = -1;    //Number of pings left until we lose our connection
		var thankYouMessage = " Thank you for unplugging the network cable. This configuration process could take a few minutes to complete. You can close your browser now and use the Photonic3D Client to find your printer.";
		this.loadingNetworksMessage = "--- Loading wifi networks from server ---"
		
		function attachToHost() {
			controller.hostSocket = cwhWebSocket.connect("services/hostNotification", $scope).onJsonContent(function(hostEvent) {
				controller.restartMessage = " " + hostEvent.message;	
				if (hostEvent.notificationEvent == "Ping") {
					var unmatchedPingCheck = function() {
						controller.hostSocket.sendMessage(hostEvent);
						
						if (unmatchedPing === 0) {
							controller.restartMessage = thankYouMessage;
							unmatchedPing = -1;//Start over from scratch if we get another ping!!!
						} else {
							unmatchedPing--;
							$interval(unmatchedPingCheck, timeoutValue, 1);
						}
					}
					
					if (unmatchedPing === -1) {
						$interval(unmatchedPingCheck, timeoutValue, 1);
					}
					
					unmatchedPing = maxUnmatchedPings;
				}
			}).onClose(function() {
				controller.restartMessage = thankYouMessage;
			});
			if (controller.hostSocket == null) {
				$scope.$emit("MachineResponse",  {machineResponse: {command:"Browser Too Old", message:"You will need to use a modern browser to run this application."}});
			}
		}
		
		this.connectToWireless = function connectToWireless() {
			controller.shutdownInProgress = true;
			$http.put("services/machine/wirelessConnect", controller.selectedNetworkInterface).then(
		    		function (data) {
		    			controller.restartMessage = " Waiting for host to start monitoring process.";
		    			$('#editModal').modal();
		    			
		    			$http.post("services/machine/startNetworkRestartProcess/600000/" + timeoutValue + "/" + maxUnmatchedPings).then(
		    		    		function (data) {
		    		    			controller.restartMessage = " Network monitoring has been setup.";
		    		    		},
		    		    		function (error) {
		    		    			controller.restartMessage = " Print host was unable to start network monitoring process. Click cancel."
		    		    		}
		    		    )
		    		},
		    		function (error) {
 	        			$scope.$emit("HTTPError", {status:error.status, statusText:error.data});
 	        			controller.shutdownInProgress = false;
		    		}
		    )
		};
		
		this.saveSkin = function saveSkin(skin){
		     $http.put("services/settings/skins", skin).then(function () {
		    	 controller.loadSkins();
	         })
		};
		
		//TODO: this needs to be attached to more than just the cancel button so that we can kill the web socket.
		this.cancelRestartProcess = function cancelRestartProcess() {
			$http.post("services/machine/cancelNetworkRestartProcess").then(
		    		function (data) {
		    			controller.shutdownInProgress = false;
		    		},
		    		function (error) {
		    			controller.shutdownInProgress = false;
 	        			$scope.$emit("HTTPError", {status:error.status, statusText:error.data});
		    		}
		    )
		}
		this.saveEmailSettings = function saveEmailSettings() {
			if (!Array.isArray(controller.emailSettings.notificationEmailAddresses)) {
				controller.emailSettings.notificationEmailAddresses = [controller.emailSettings.notificationEmailAddresses];
			}
			if (!Array.isArray(controller.emailSettings.serviceEmailAddresses)) {
				controller.emailSettings.serviceEmailAddresses = [controller.emailSettings.serviceEmailAddresses];
			}
			$http.put("services/settings/emailSettings", controller.emailSettings).then(
		    		function (data) {
		    			alert("Email settings saved.");
		    		},
		    		function (error) {
 	        			$scope.$emit("HTTPError", {status:error.status, statusText:error.data});
		    		}
		    )
		};
		
		this.loadSkins = function loadSkins() {
		  	$http.get("services/settings/skins/list").success(
				function (data) {
					$scope.availableSkins = data;
					console.log(data);
				}
			);
		}
		
		$http.get("services/settings/emailSettings").success(
	    		function (data) {
	    			controller.emailSettings = data;
	    		})
	    		
		$http.get("services/machine/wirelessNetworks/list").success(
	    		function (data) {
	    			controller.networkInterfaces = data;
	    			controller.loadingNetworksMessage = "Select a wifi network";
	    		})
	
		attachToHost();
		this.loadSkins();
	}])
})();
