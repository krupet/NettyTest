CREATE TABLE `connections1` (
`id` int(10) unsigned NOT NULL AUTO_INCREMENT,
`ip` varchar(100) NOT NULL,
`uri` varchar(100) NOT NULL,
`timestamp` varchar(100) NOT NULL,
`redirect` varchar(10) NOT NULL,
`get_bytes` bigint DEFAULT NULL,
`sent_bytes` bigint DEFAULT NULL,
PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8