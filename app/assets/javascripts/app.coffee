estimatorApp = window.angular.module('estimatorApp' , [])

estimatorApp.controller('MainController', (($scope, $http, $log) ->
		$log.info("angular init")
		startWS = ->
			wsUrl = jsRoutes.controllers.Application.indexWS().webSocketURL()
	  
			$scope.socket = new WebSocket(wsUrl)
			$scope.socket.onmessage = (msg) ->
				$scope.$apply( ->	
					$scope.result = JSON.parse(msg.data)
					$('pre').hide().show())

		$scope.estimate = ->
			$log.info("estimating " + $scope.request.url)
			$http.get(jsRoutes.controllers.Application.estimate($scope.request.url).url).success( -> )
		
		$scope.result = {}
	  
		$scope.request = {}

		startWS()
	)
) 