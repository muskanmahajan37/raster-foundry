package com.azavea.rf.tile.tool

import com.azavea.rf.common.Authentication
import com.azavea.rf.tile.image._
import com.azavea.rf.tile._
import com.azavea.rf.tool.ast._
import com.azavea.rf.database.Database
import com.azavea.rf.database.tables.Tools
import com.azavea.rf.datamodel._
import com.azavea.rf.tool.ast.codec._
import com.azavea.rf.tool.ast._
import com.azavea.rf.tool.op._

import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.io._
import geotrellis.raster.op._
import geotrellis.raster.render.{Png, ColorRamp, ColorMap}
import geotrellis.raster.io.geotiff._
import geotrellis.vector.Extent
import geotrellis.slick.Projected
import geotrellis.spark._
import geotrellis.spark.tiling._
import geotrellis.proj4._
import com.typesafe.scalalogging.LazyLogging
import cats.data._
import cats.data.Validated._
import cats.implicits._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import java.util.UUID
import java.io._


object TileSources extends LazyLogging {
  /** This source will return the raster for all of zoom level 1 */
  def cachedGlobalSource(implicit database: Database): RFMLRaster => Future[Option[Tile]] = { r =>
    r match {
      case scene @ SceneRaster(sceneId, Some(band)) =>
        LayerCache.layerTileForExtent(sceneId, 1, WebMercator.worldExtent)
          .map(tile => tile.bands(band)).value

      case scene @ SceneRaster(sceneId, None) =>
        logger.warn(s"Request for $scene does not contain band index")
        Future.successful(None)

      case project @ ProjectRaster(projId, Some(band)) =>
        Mosaic.fetchRenderedExtent(projId, 1, Some(Projected(WebMercator.worldExtent.toPolygon, 4326)))
          .map({ tile => tile.bands(band) }).value

      case project @ ProjectRaster(projId, None) =>
        logger.warn(s"Request for $project does not contain band index")
        Future.successful(None)
    }
  }

  def cachedTmsSource(implicit database: Database): (RFMLRaster, Int, Int, Int) => Future[Option[Tile]] = { (r, z, x, y) =>
    r match {
      case scene @ SceneRaster(sceneId, Some(band)) =>
        LayerCache.layerTile(sceneId, z, SpatialKey(x, y))
          .map( tile => tile.bands(band)).value

      case scene @ SceneRaster(sceneId, None) =>
        logger.warn(s"Request for $scene does not contain band index")
        Future.successful(None)

      case project @ ProjectRaster(projId, Some(band)) =>
        Mosaic.fetch(projId, z, x, y)
          .map( tile => tile.bands(band)).value

      case project @ ProjectRaster(projId, None) =>
        logger.warn(s"Request for $project does not contain band index")
        Future.successful(None)

      case _ =>
        Future.failed(new Exception(s"Cannot handle $r"))
    }
  }
}