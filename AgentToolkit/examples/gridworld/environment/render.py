########################################################################
# NOTE:
# This file was partially adapted from
# https://github.dev/iglu-contest/gridworld/blob/bac482340b1d4a9eb7a876dee99f83ccb88e4c56/gridworld/render.py
########################################################################

from examples.gridworld.environment.utils import WHITE, GREY, cube_vertices, cube_normals, id2texture, id2top_texture
from PIL import Image
import platform
import time
import numpy as np
import math
from filelock import FileLock
from pyglet import image
from scipy.ndimage import gaussian_filter
from pyglet.graphics import Batch, TextureGroup
from pyglet import app
from pyglet.gl import *
from pyglet.window import Window
import os
import pyglet
pyglet.options['shadow_window'] = False
if os.environ.get('IGLU_HEADLESS', '1') == '1':
    import pyglet
    pyglet.options["headless"] = True
    devices = os.environ.get('CUDA_VISIBLE_DEVICES')
    if devices is not None and devices != '':
        pyglet.options['headless_device'] = int(devices.split(',')[0])


_60FPS = 1./60
PLATFORM = platform.system()


def setup_fog():
    """ Configure the OpenGL fog properties.
    """
    glEnable(GL_FOG)
    glFogfv(GL_FOG_COLOR, (GLfloat * 4)(0.5, 0.69, 1.0, 1))
    glHint(GL_FOG_HINT, GL_DONT_CARE)
    glFogi(GL_FOG_MODE, GL_LINEAR)
    glFogf(GL_FOG_START, 25.0)
    glFogf(GL_FOG_END, 30.0)


def setup():
    """ Basic OpenGL configuration.

    """
    glClearColor(0.5, 0.69, 1.0, 1)
    glEnable(GL_CULL_FACE)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
    # setup_fog()


class Renderer(Window):
    TEXTURE_PATH = 'texture.png'

    def __init__(self, model, agent, initialize_model=True, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.model = model
        self.agent = agent
        self.model.add_callback('on_add', self.add_block)
        self.model.add_callback('on_remove', self.remove_block)
        self.batch = Batch()
        dir_path = os.path.dirname(__file__)
        TEXTURE_PATH = os.path.join(dir_path, Renderer.TEXTURE_PATH)
        with FileLock(f'/tmp/mylock'):
            self.texture_group = TextureGroup(image.load(TEXTURE_PATH).get_texture())
        self.overlay = False
        self._shown = {}
        self.label = pyglet.text.Label('', font_name='Arial', font_size=18,
                                       x=10, y=self.height - 10, anchor_x='left', anchor_y='top',
                                       color=(0, 0, 0, 255))

        if initialize_model:
            self.model.initialize()

        self.buffer_manager = pyglet.image.get_buffer_manager()
        self.last_frame_dt = 0
        self.realtime_rendering = os.environ.get('IGLU_RENDER_REALTIME', '0') == '1'
        if not pyglet.options['headless']:
            app.platform_event_loop.start()
            self.dispatch_event('on_enter')

    def set_2d(self):
        """ Configure OpenGL to draw in 2d.

        """
        width, height = self.get_size()
        glDisable(GL_DEPTH_TEST)
        viewport = self.get_viewport_size()
        glViewport(0, 0, max(1, viewport[0]), max(1, viewport[1]))
        glMatrixMode(GL_PROJECTION)
        glLoadIdentity()
        glOrtho(0, max(1, width), 0, max(1, height), -1, 1)
        glMatrixMode(GL_MODELVIEW)
        glLoadIdentity()

    def set_3d(self):
        """ Configure OpenGL to draw in 3d.

        """
        width, height = self.get_size()
        glEnable(GL_DEPTH_TEST)
        viewport = self.get_viewport_size()
        glViewport(0, 0, max(1, viewport[0]), max(1, viewport[1]))
        glMatrixMode(GL_PROJECTION)
        glLoadIdentity()
        gluPerspective(90.0, width / float(height), 0.1, 30.0)
        glMatrixMode(GL_MODELVIEW)
        glLoadIdentity()
        x, y = self.agent.rotation
        glRotatef(x, 0, 1, 0)
        glRotatef(-y, math.cos(math.radians(x)), 0, math.sin(math.radians(x)))
        x, y, z = self.agent.position
        glTranslatef(-x, -y, -z)

    def on_draw(self):
        """
        Called by pyglet to draw the canvas.
        """
        self.clear()
        self.set_3d()
        glColor3d(1, 1, 1)
        self.batch.draw()

        if self.overlay:
            self.draw_focused_block()
            self.set_2d()
            self.draw_label()
            self.draw_reticle()

    def render(self):
        if not pyglet.options['headless']:
            t = time.perf_counter()
            self.switch_to()
        self.on_draw()
        width, height = self.get_size()
        if PLATFORM == 'Darwin' and not pyglet.options["headless"]:
            new_shape = (height * 2, width * 2, 4)
        else:
            new_shape = (height, width, 4)
        rendered = np.asanyarray(
            self.buffer_manager
            .get_color_buffer()
            .get_image_data()
            .get_data()
        ).reshape(new_shape)[::-1]
        if not pyglet.options['headless']:
            dt = time.perf_counter() - t
            self.last_frame_dt += dt
            if self.realtime_rendering:
                self.flip()
            if self.last_frame_dt >= _60FPS:
                if not self.realtime_rendering:
                    self.flip()
                app.platform_event_loop.step(dt)
                self.last_frame_dt = 0.
        return rendered

    def add_block(self, position, texture_id, **kwargs):
        x, y, z = position
        top_only = texture_id in [WHITE, GREY]
        texture = (id2top_texture if top_only else id2texture)[texture_id]
        vertex_data = cube_vertices(x, y, z, 0.5, top_only=top_only)
        texture_data = list(texture)
        # create vertex list
        # FIXME Maybe `add_indexed()` should be used instead
        self._shown[position] = self.batch.add(4 if top_only else 24, GL_QUADS, self.texture_group,
                                               ('v3f/static', vertex_data),
                                               ('t2f/static', texture_data),
                                               )

    def remove_block(self, position, **kwargs):
        if position in self._shown:
            self._shown.pop(position).delete()

    def draw_focused_block(self):
        """ Draw black edges around the block that is currently under the
        crosshairs.

        """
        block = self.model.get_focused_block(self.agent)
        if block:
            x, y, z = block
            vertex_data = cube_vertices(x, y, z, 0.51)
            glColor3d(0, 0, 0)
            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE)
            pyglet.graphics.draw(24, GL_QUADS, ('v3f/static', vertex_data))
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL)

    def draw_label(self):
        """ Draw the label in the top left of the screen.

        """
        x, y, z = self.agent.position
        i = self.agent.inventory
        self.label.text = f'{int(pyglet.clock.get_fps()):02d} ({x:.2f}, {y:.2f}, {z:.2f}) ' \
            f'{len(self._shown)} / {len(self.model.world)} ' \
            f'({i[0]}, {i[1]}, {i[2]}, {i[3]}, {i[4]}, {i[5]})'
        self.label.draw()

    def draw_reticle(self):
        """ Draw the crosshairs in the center of the screen.

        """
        glColor3d(0, 0, 0)
        self.reticle.draw(GL_LINES)
