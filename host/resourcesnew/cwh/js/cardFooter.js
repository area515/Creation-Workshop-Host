(function() {
	var cwhApp = angular.module('cwhApp');

	cwhApp.controller("CardFooter", function ($scope, $http, $window, $uibModal) {
		$scope.executeDiagnostic = function executeDiagnostic() {
	        $http.get("services/machine/executeDiagnostic").success(
	        		function (data) {
	        			$scope.$emit("MachineResponse", {machineResponse: {command:"Executed Diagnostic", message:"An email with diagnostic support information has been sent to your configured support email contact.", response:true}, successFunction:null, afterErrorFunction:null});
	        		}).error(
    				function (data, status, headers, config, statusText) {
 	        			$scope.$emit("HTTPError", {status:status, statusText:data});
	        		})
		}
		
		$scope.restorePhotonic = function restorePhotonic() {
			var fileChosenModal = $uibModal.open({
		        animation: true,
		        templateUrl: 'upload.html',
		        controller: 'UploadFileController',
		        size: "lg",
		        resolve: {
		        	title: function () {return "WARNING!!! Restore Photonic3D from Backup";},
		        	supportedFileTypes: function () {return null},
		        	getRestfulFileUploadURL: function () {return function (filename) {return '/services/machine/restoreFromBackup';}},
		        	getRestfulURLUploadURL: null
		        }
			});
			
			fileChosenModal.closed.then(function (uploadedFile) {
				if (uploadedFile != null) {
					alert("Please restart Photonic3D to have your new settings take effect.");
				}
			});
		}
		
		$scope.downloadDiagnostic = function downloadDiagnostic() {
        	$window.location.href = "services/machine/downloadDiagnostic/LogBundle.zip";
        }
	})

})();