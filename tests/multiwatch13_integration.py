#!/usr/bin/env python3
"""Front-13 real rclpy/native acceptance (only inside an isolated container)."""
import json
import sys
import device_multiwatch_fixture as fixture

if __name__ == '__main__':
    result = fixture.host_main(sys.argv[1])
    required = {'mixed_rates', 'fifth_rejected', 'negative_qos', 'qos_recovery',
                'topic_failure_0', 'reader_failure_0', 'topic_failure_2', 'reader_failure_2',
                'same_topic', 'remove_same_topic', 'recreate', 'rosout_clear', 'stop'}
    assert set(result['cases']) == required, result
    assert result['classification'] == 'host-isolated-real-dds-not-android'
    print('MULTIWATCH13_RESULT '+json.dumps(result, sort_keys=True), flush=True)
