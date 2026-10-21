# Refresh pins explicitly; missing exact packages must fail rather than float.
FROM ros:jazzy-ros-base-noble@sha256:103b6c417973394659af6804d4917cd489b91e5ed22eb7bef263025a3bed2410
RUN apt-get update && apt-get install -y --no-install-recommends \
    ros-jazzy-rmw-cyclonedds-cpp=2.2.4-1noble.20260911.095700 \
    ros-jazzy-cyclonedds=0.10.5-1noble.20260225.142613 \
    ros-jazzy-iceoryx-binding-c=2.0.6-1noble.20260225.140829 \
    ros-jazzy-iceoryx-hoofs=2.0.6-1noble.20260225.055330 \
    ros-jazzy-iceoryx-posh=2.0.6-1noble.20260225.135341 \
    acl=2.3.2-1build1.1 libacl1-dev=2.3.2-1build1.1 libattr1-dev=1:2.5.2-1ubuntu0.1 \
    && rm -rf /var/lib/apt/lists/*
COPY fixture.py /fixture/fixture.py
COPY fixture-loopback.xml /fixture/loopback.xml
ENV ROS_DOMAIN_ID=67 ROS_LOCALHOST_ONLY=1 PYTHONDONTWRITEBYTECODE=1 ROS_LOG_DIR=/tmp/ros/log HOME=/tmp
USER 65534:65534
CMD ["timeout", "420", "python3", "/fixture/fixture.py"]
