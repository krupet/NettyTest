CREATE TABLE `connections` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `ip` varchar(100) NOT NULL,
  `uri` varchar(100) NOT NULL,
  `timestamp` varchar(100) NOT NULL,
  `redirect` varchar(10) NOT NULL,
  `get_bits` bigint,
  `sent_bits` bigint,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8