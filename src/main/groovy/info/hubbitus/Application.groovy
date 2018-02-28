package info.hubbitus

import info.hubbitus.cli.CliOptions
import info.hubbitus.cli.JCommanderAutoWidth

/**
 * @author Pavel Alexeev.
 * @since 2017-09-16 13:44.
 */


//client.query('egaisapp/optionsByTag/list').dump()
//println client.getCatalog().dump()
//println client.getTags('egaisapp').dump()
//println client.getApplicationTagsDetails('egaisapp').dump()
//def res = client.getListOfTagsWithBuildDates('egaisapp')

//def tagInfo = client.getTagInfo('egaisapp', '_eg-4951_attachments_migration_db-to-riak_.ff808da')
//def ttt = 777
//def resp = client.deleteTag('egaisapp', tagInfo.responseBase.headergroup.getHeaders('Docker-Content-Digest')[0].getValue())
//def tt = 77


new RegistryCleaner(args)
