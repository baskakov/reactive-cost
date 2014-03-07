estimatorApp = window.angular.module('estimatorApp', ['ngSanitize'])

estimatorApp.controller('MainController', (($scope, $http, $log, $location) ->
  $log.info("angular init")
  startWS = ->
    wsUrl = jsRoutes.controllers.Application.indexWS().webSocketURL()

    $scope.socket = new WebSocket(wsUrl)
    $scope.socket.onmessage = (msg) ->
      $scope.$apply(->
        $scope.result = JSON.parse(msg.data))

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
    $http.get(jsRoutes.controllers.Application.estimate($scope.request.url).url).success(->)

  $scope.result = {}

  $scope.request = {}

  startWS()

  hashToModel()
)
).filter('formatToHtml', ->
  ((input) -> if input then input.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g,"&gt;").replace(/^(\r\n|\r|\n)*/g, '').replace(/(\r\n|\r|\n)/g, '<br/>') else input)
)