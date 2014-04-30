estimatorApp = window.angular.module('estimatorApp', ['ngSanitize'])

estimatorApp.controller('MainController', (($scope, $http, $log, $location, $q) ->
  S4 = -> (((1+Math.random())*0x10000)|0).toString(16).substring(1)				
		
  $scope.windowGuid = (S4()+S4()+"-"+S4()+"-"+S4()+"-"+S4()+"-"+S4()+S4()+S4())	
		
  $log.info("angular init with guid " + $scope.windowGuid)
  
  startWS = ->
    wsUrl = jsRoutes.controllers.Application.indexWS($scope.windowGuid).webSocketURL()

    $scope.socket = new WebSocket(wsUrl)
    $scope.socket.onmessage = (msg) ->
      $scope.$apply(->
        $scope.result = JSON.parse(msg.data))
        
  stopWS = ->
      $scope.socket.close() if $scope.socket

  hashToModel = ->
    hash = $location.hash()
    if hash && hash.length > 0 && hash != $scope.request.url
      $scope.request.url = hash
      $scope.estimate()

  modelToHash = ->
    $location.hash($scope.request.url)


  $scope.estimate = ->
    $log.info("estimating " + $scope.request.url)
    modelToHash()
    if $scope.mode == "post"
        $scope.canceler = $q.defer();
        $http(
            method: 'GET'
            url: jsRoutes.controllers.Application.estimateRest($scope.request.url).url 
            timeout: $scope.canceler.promise).success((data) ->
                $log.info("received " + data.message)
                $scope.canceler = null
                $scope.result = data).error( -> $scope.canceler = null)
    else if $scope.mode == "socket"
        $http.get(jsRoutes.controllers.Application.estimate($scope.windowGuid, $scope.request.url).url).success(->)

  $scope.result = {}

  $scope.request = {}
  
  $scope.mode = "post"
  
  $scope.setMode = (mode) ->
        if mode != $scope.mode
            if mode == "post"
                stopRequest()
                $scope.mode = mode
                stopWs()
            else if mode == "socket"
                stopRequest()
                $scope.mode = mode
                startWS()
                
  stopRequest = -> 
      $scope.canceler.resolve() if $scope.canceler

  hashToModel()
)
).filter('formatToHtml', ->
  ((input) -> if input then input.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g,"&gt;").replace(/^(\r\n|\r|\n)*/g, '').replace(/(\r\n|\r|\n)/g, '<br/>') else input)
)
