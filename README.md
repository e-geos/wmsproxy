Easy WMS Proxy server

You can download using git clone, and after that, compile using maven. Now you have a war file.

To test it use something like this

http://localhost/wmsproxy-0.1/wms?service=WMS&version=1.1.0&request=GetMap&layers=mylayer&styles=&bbox=355710.821306835,4606036.86437748,439156.898757636,4741620.60032973&width=472&height=768&srs=EPSG:32633&format=image%2Fpng

if you have a geoserver working call like this:

http://localhost/geoserver/geo/wms?service=WMS&version=1.1.0&request=GetMap&layers=mylayer&styles=&bbox=355710.821306835,4606036.86437748,439156.898757636,4741620.60032973&width=472&height=768&srs=EPSG:32633&format=image%2Fpng
