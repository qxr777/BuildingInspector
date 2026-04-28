docker stop dev_building_inspector || true
docker rm dev_building_inspector || true
docker pull crpi-gxv0234hwyx1jbhm.cn-beijing.personal.cr.aliyuncs.com/qxr777/dev-building-inspector:1.0
docker run -d --name dev_building_inspector -p 8090:80 crpi-gxv0234hwyx1jbhm.cn-beijing.personal.cr.aliyuncs.com/qxr777/dev-building-inspector:1.0
