var SakaiDrive = function(baseURL, collectionId) {
  this.baseURL = baseURL;
  this.collectionId = collectionId;

  this.setupPreviewer();
  this.setupRow();
  this.setupDragAndDrop();
  this.setupContextMenu();
  this.setupFolderTree();
  this.ajaxLinkHandler();
};

SakaiDrive.prototype.setupPreviewer = function() {
  var self = this;

  $('.drive-file.pdf a, .drive-file.image a, .drive-file.excel a, .drive-file.ppt a, .drive-file.word a').on('click', function(event) {
    event.preventDefault();
    event.stopPropagation();

    var $preview = self.getPreviewTemplate();

    $preview.find('iframe').attr('src', self.VIEW_URL + encodeURIComponent(baseURL + 'preview?path=' + $(this).data('path')));

    $(document.body).append($preview);

    function resize() {
      $preview.height($(window).height());
      $preview.find('.blockout, iframe').height($preview.height());
    };

    $(window).on('resize', resize);
    resize();

    $(document.body).addClass('sakai-drive-preview-open');

    $preview.on('click', '.close', function() {
      $(document.body).removeClass('sakai-drive-preview-open');
      $preview.remove();
      $(window).off('resize', resize);
    });

    return false;
  });
};

SakaiDrive.prototype.getPreviewTemplate = function() {
  return $($("#previewTemplate").html().trim());
}


SakaiDrive.prototype.setupRow = function() {
    var self = this;
    var lastSelectedRow = null;

    function toggleRow($tr, force_active) {
        if (force_active) {
            $tr.addClass('active');
        } else {
            $tr.toggleClass('active');
        }
        if ($tr.is('.active')) {
            lastSelectedRow = $tr;
        } else if (lastSelectedRow[0] == $tr[0]) {
            lastSelectedRow = null;
        }
    };

    function clearAll() {
        $('.sakai-resources-table tbody tr.active').removeClass('active');
        lastSelectedRow = null;
    };

    function selectRowsBetweenIndexes(a, b) {
        var min = Math.min(a, b);
        var max = Math.max(a, b);

        for (var i = min; i <= max; i++) {
            var $row = $($('.sakai-resources-table tbody tr').get(i));
            toggleRow($row, true);
        }
    };

    $('.sakai-resources-table').on('mousedown', 'tbody tr', function(event) {
        var $tr = $(event.target).closest('tr');

        if (event.ctrlKey || event.metaKey) {
            toggleRow($tr);
        } else if (event.shiftKey) {
            if (lastSelectedRow) {
                selectRowsBetweenIndexes(lastSelectedRow.index(), $tr.index());
            } else {
                toggleRow($tr);
            }
        } else if (!$tr.is('.active')) {
            clearAll();
            toggleRow($tr);
        }
    }).on('click', 'tbody tr', function(event) {
        var $tr = $(event.target).closest('tr');

        if (!event.ctrlKey && !event.metaKey && !event.shiftKey) {
            clearAll();
            toggleRow($tr, true);
        }
    }).on('dblclick', 'tbody tr', function(event) {
        var $tr = $(this).closest('tr');

        if ($(event.target).is('a')) {
            return true;
        }

        $tr.find('td.name a')[0].click();

        return false;
    });;
};

SakaiDrive.prototype.doMove = function(target) {
    var self = this;

    var sources = $('.sakai-resources-table tbody tr.active').map(function() {
        return $(this).find('[data-path]').data('path');
    }).toArray();

    var target_uri = $(target).find('[data-path]').data('path');

    $.ajax(baseURL + "move",
           {
               type: "POST",
               data: {
                   'inline_target_listing': true,
                   'source[]': sources,
                   target: target_uri,
               }
           }).success(function (content, status, xhr) {
               self.reloadPane(self.baseURL + 'sakai-resources' + target_uri, {}, content);
           });
};


SakaiDrive.prototype.setupDragAndDrop = function() {
    var self = this;

    // setup draggables
    $('.sakai-resources-table tbody tr').addClass('sakai-resource-draggable');

    $('.sakai-resource-draggable').draggable({
        opacity: 0.8,
        helper: function(event) {
            var $active = $('.sakai-resources-table tbody tr.active');
            var $helper = $('<div>');

            if ($active.length > 1) {
                var $label = $('<div>').append('<i class="fa fa-files-o" aria-hidden="true"></i> ').append($active.length + ' items');
                $label.addClass('name');
                $label.width(200);
                $helper.append($label);
            } else {
                var $tr = $(event.target).closest('.sakai-resource-draggable');
                var $name = $('<div>').append($tr.find('td.name').html());
                $name.addClass('name');
                $name.width($active.find('td.name a').width() + 50);
                $helper.append($name);
            }

            $helper.addClass('sakai-resource-drag-helper');
            $helper.width($($active.get(0)).width());
            $helper.height($($active.get(0)).height());
            return $helper;
        },
        cursorAt: {
            left: 5,
        },
        start: function(event, ui) {
            $(ui.helper).animate({
                'width': $(ui.helper).find('> .name').width()
            });
        }
    });

    // setup droppables
    self.addDroppables($('.sakai-resources .sakai-resources-breadcrumbs .breadcrumb-item:not(.active)'));
    $('.sakai-resources .sakai-resources-table .drive-folder').map(function() {
        self.addDroppables($(this).closest('tr'));
    });
};

SakaiDrive.prototype.addDroppables = function($droppables) {
    var self = this;

    var autoExpandTimeout;
    $droppables.addClass('sakai-resource-dropzone');
    $droppables.droppable({
        accept: function(draggable) {
            return draggable.is('.sakai-resource-draggable');
        },
        hoverClass: 'sakai-resource-dropzone-active',
        drop: function(event, ui) {
            self.doMove(event.target);
        },
        tolerance: 'pointer',
        over: function(event, ui) {
            // auto expand folder after 1s
            var $droppable = $(event.target).closest('.sakai-resource-dropzone');
            if ($droppable.closest('.sakai-resources-tree').length == 1) {
                if ($droppable.parent().is('.has-children:not(.expanded)')) {
                    autoExpandTimeout = setTimeout(function() {
                       $droppable.parent().find('.drive-folder-toggle').trigger('click')
                    }, 1000);
                }
            }
        },
        out: function(event, ui) {
            if (autoExpandTimeout != null) {
                clearTimeout(autoExpandTimeout);
                autoExpandTimeout = null;
            }
        }
    });
}


SakaiDrive.prototype.setupContextMenu = function() {
    $('.sakai-resources-table tbody tr td').on("contextmenu", function (e) {
        // remove already showing menus
        $('.sakai-drive-context-menu').hide().remove();

        if (e.ctrlKey) return;

        var template = TrimPath.parseTemplate($("#contextMenuTemplate").html().trim().toString());
        var $target = $(e.target);
        var $menu = $(template.process({
          isFolder: $target.closest('tr').find('.drive-folder').length > 0
        }));
        $menu.css({
                position: "absolute",
                left: e.clientX,
                top: $(e.target).offset().top + 10
            })
            .off('click')
            .on('click', 'a', function (e) {
                $menu.hide();

                var $selectedMenu = $(e.target);

                if ($selectedMenu.is('.sakai-drive-context-menu-open')) {
                  $target.closest('tr').find('td.name a')[0].click();
                } else {
                  alert('TODO :)');
                }

                $menu.remove();

                return false;
            });

        $(document.body).append($menu);
        $menu.show();

        //make sure menu closes on any click
        $('body').on('click', function () {
            $menu.hide().remove();
        });

        return false;
    });
};

SakaiDrive.prototype.setupFolderTree = function() {
  var self = this;
  var template = TrimPath.parseTemplate($("#treeFolderTemplate").html().trim().toString());

  function renderFolder(folder, $target) {
    var $li = $(template.process(folder));
    $target.append($li);

    if (self.collectionId == folder.id) {
      $li.addClass('active');
    }

    if (folder.children.length > 0) {
      $li.addClass('has-children');
      var $ul = $('<ul>');
      $.each(folder.children, function() {
        renderFolder(this, $ul);
      });
      $li.append($ul);
    }
  };

  $.getJSON(baseURL+'/folder-tree', function(root) {
    renderFolder(root, $('.sakai-resources-tree'));
    self.addDroppables($('.sakai-resources-tree .drive-folder'));

    $('.sakai-resources-tree li.active').each(function() {
      $(this).addClass('expanded');
      $(this).parentsUntil('.sakai-resources-tree', 'li').addClass('expanded');
    });

    // TODO store toggle state in browser store so remembered upon refresh? 
    $('.sakai-resources-tree').on('click', 'li.has-children > .drive-folder > .drive-folder-toggle', function() {
      $(this).closest('li').toggleClass('expanded');
    });
  });
};

SakaiDrive.prototype.VIEW_URL = '/drive-tool/pdfjs/web/viewer.html?file=';


function GoogleDrive(rootElt, baseURL, options) {
  this.root = rootElt;
  this.baseURL = baseURL;
  this.options = options;

  this.scrollContainer = this.root.find('.scroll-container');
  this.list = this.root.find('.file-list');

  this.nextPageToken = null;

  this.currentSearchTerm = '';

  this.loadThresholdPx = parseInt(this.scrollContainer.height()) * 4;
  this.currentlyLoading = false;

  this.setupList();
  this.setupScrollHandling();

  this.search = this.root.find('.file-search');
  this.setupSearch();

  this.getFiles();
};

GoogleDrive.prototype.setupList = function() {
  this.listOverlay = $('<div class="list-overlay" />');

  this.listOverlay
      .css('position', 'absolute')
      .css('top', this.scrollContainer.position().top)
      .css('left', this.scrollContainer.position().left);

  this.root.append(this.listOverlay);
}

GoogleDrive.prototype.showOverlay = function () {
  if (this.list.find('li').length == 0) {
    /* Nothing to overlay */
    return;
  }
  this.listOverlay
      .css('width', this.scrollContainer.width())
      .css('height', this.scrollContainer.height())
      .show();
};

GoogleDrive.prototype.hideOverlay = function () {
  this.listOverlay.hide();
};

GoogleDrive.prototype.showNoMatches = function () {
  this.root.find('.no-matches-msg').show();
};

GoogleDrive.prototype.hideNoMatches = function () {
  this.root.find('.no-matches-msg').hide();
};

GoogleDrive.prototype.setupScrollHandling = function() {
  var self = this;

  self.scrollContainer.on('scroll', function () {
    if (self.currentlyLoading || !self.nextPageToken) {
      /* Nothing needs doing */
      return true;
    }

    var scrollPosition = self.scrollContainer.scrollTop() + self.scrollContainer.height();

    if (scrollPosition >= (self.list.height() - self.loadThresholdPx)) {
      /* Load next page */
      self.currentlyLoading = true;
      self.getFiles(self.nextPageToken, {
        complete: function () {
          self.currentlyLoading = false;
        }
      });
    }

    return true;
  });
};

GoogleDrive.prototype.clearSearch = function() {
  this.currentSearchTerm = '';
  this.search.val('');
};

GoogleDrive.prototype.setupSearch = function() {
  var self = this;
  var updateTimer = null;

  self.search.on('input', function () {
    if (updateTimer) {
      clearTimeout(updateTimer);
    }

    updateTimer = setTimeout(function () {
      updateTimer = null;
      self.handleSearchChange();
    }, 250);
  });
};

GoogleDrive.prototype.handleSearchChange = function () {
  var self = this;

  // ensure recent tab is visible
  // this.root.closest('.google-drive').find('.google-drive-menu a[href="#googledriverecent"]').tab('show');

  if (!self.root.is(':visible')) {
    return true;
  }

  console.log("handling search change");

  if (self.currentlyLoading) {
    console.log("defer...");
    /* Defer handling until we finish loading */
    setTimeout(function () {
      self.handleSearchChange();
    }, 200);

    return true;
  }

  var query = self.search.val();

  if (query == this.currentSearchTerm) {
    /* Nothing to do anyway! */
    console.log("Nothing to do");
    return true;
  }

  console.log("here we go!");
  self.currentSearchTerm = (query || '');
  self.nextPageToken = null;

  self.showOverlay();

  self.getFiles(null, {
    replaceList: true,
    complete: function () {
      self.hideOverlay();
    },
    data: $.extend({}, self._currentPageData || {}, {
      q: self.currentSearchTerm,
    }),
  });

  return true;
};

GoogleDrive.prototype.getFiles = function(pageToken, options) {
  var self = this;
  var fileTemplate = TrimPath.parseTemplate($("#fileTemplate").html().trim().toString());

  if (!pageToken) { pageToken = ''; }
  if (!options) { options = {} }
  if (!options.complete) { options.complete = $.noop; }
  if (!options.data) { options.data = {}; }

  var data = {};

  if (pageToken != '') {
    data = self._currentPageData;
    data.pageToken = pageToken;
  };

  self._currentPageData = $.extend({}, data, options.data);

  $.getJSON(this.baseURL + this.options.path,
            self._currentPageData,
            function(json) {
              self.hideNoMatches();

              if (options.replaceList) {
                self.list.empty();
              }

              $.each(json.files, function(index, page) {
                var html = fileTemplate.process(page);
                self.list.append(html);
              });

              self.nextPageToken = json.nextPageToken;

              if (json.files.length == 0) {
                self.showNoMatches();
              }

              options.complete();
            });
};

GoogleDrive.prototype.refreshListForFolder = function(folderId) {
  var self = this;

  self.getFiles(null, {
    replaceList: true,
    data: {
      folderId: folderId,
    },
    complete: function() {
      self.hideOverlay();
    }
  });
};


function GoogleDriveModal($modal, baseURL) {
  this.$modal = $modal;
  this.baseURL = baseURL;

  this.init();
};

GoogleDriveModal.prototype.init = function() {
  var self = this;

  $(window).resize(function() {
    self.resizeGoogleModal();
  });

  self.$modal.on('show.bs.modal', function () {
    $.ajax(baseURL + "show-google-drive",
           {
             type: "GET",
             contentType: "html",
           }).success(function (content) {
             $('#google-drive-modal .modal-body').html(content);

             self.setupTabs();

             // setup form submit
             $('#google-drive-modal .modal-footer .btn-primary').on('click', function() {
               $('#google-drive-modal .modal-body form.google-drive-add-selected-form:visible').submit();
             });
           });

    return true;
  });

  self.$modal.on('shown.bs.modal', function () {
    self.resizeGoogleModal();
  });

  self.$modal.on('click', '.file-list :checkbox', function(event) {
    if ($(this).is(':checked')) {
      $(this).closest('li').addClass('active');
    } else {
      $(this).closest('li').removeClass('active');
    }
  });
};

GoogleDriveModal.prototype.resizeGoogleModal = function() {
  if (this.$modal.is(':visible')) {
    this.$modal.find('.modal-body').height($(window).height() - this.$modal.find('.modal-header').height() - this.$modal.find('.modal-footer').height() - 150);
    this.$modal.find('.tab-content').height(this.$modal.find('.modal-body').height() - (this.$modal.find('.tab-content').offset().top - this.$modal.find('.modal-body').offset().top))
    this.$modal.find('.scroll-container').height(this.$modal.find('.tab-content').height() - (this.$modal.find('.tab-pane.active .breadcrumb').height() || 0));
  }
};

GoogleDriveModal.prototype.setupTabs = function() {
    var self = this;

    // bootstap tabs please
    self.$modal.find('.google-drive-menu').tab();

    // Recent/Search
    self.recentDrive = new GoogleDrive(self.$modal.find('#googledriverecent'), baseURL, {
      path: '/drive-data',
    });

    self.$modal.find('.google-drive-menu a[href="#googledriverecent"]').on('show.bs.tab', function() {
      // nothing at the moment
    });

    // My Drive
    self.myDrive = null;
    self.$modal.find('.google-drive-menu a[href="#googledrivehome"]').on('show.bs.tab', function() {
      // load the drive home
        if (self.myDrive == null) {
          // load my drive (for root context)
          self.myDrive = new GoogleDrive($('#googledrivehome'), baseURL, {
            path: '/my-drive-data',
          });


          $("#googledrivehome").on('click', '.google-drive-folder, .breadcrumb a', function() {
              var $link = $(this);
              var text = $link.text();
              var folder = $link.data('id');

              self.myDrive.showOverlay();

              self.myDrive.clearSearch();

              if ($link.closest('.breadcrumb').length == 1) {
                  $link.closest('li').nextAll().remove();
                  $link.closest('li').addClass('active');
              } else {
                  var breadcrumb = $('<li>');
                  var a = $('<a>').attr('href','#').data('id', folder).text(text);
                  breadcrumb.append(a);
                  breadcrumb.addClass('active');
                  $("#googledrivehome .breadcrumb .active").removeClass('active');
                  $("#googledrivehome .breadcrumb").append(breadcrumb);
              }

              self.myDrive.refreshListForFolder(folder);
          });
       }
    });

    // Starred
    self.starredDrive = null;
    self.$modal.find('.google-drive-menu a[href="#googledrivestarred"]').on('show.bs.tab', function() {
      // load the drive home
        if (self.starredDrive == null) {
          // load my drive (for root context)
          self.starredDrive = new GoogleDrive($('#googledrivestarred'), baseURL, {
            path: '/starred-drive-data',
          });


          $("#googledrivestarred").on('click', '.google-drive-folder, .breadcrumb a', function() {
              var $link = $(this);
              var text = $link.text();
              var folder = $link.data('id');

              self.starredDrive.showOverlay();

              self.starredDrive.clearSearch();

              if ($link.closest('.breadcrumb').length == 1) {
                  $link.closest('li').nextAll().remove();
                  $link.closest('li').addClass('active');
              } else {
                  var breadcrumb = $('<li>');
                  var a = $('<a>').attr('href','#').data('id', folder).text(text);
                  breadcrumb.append(a);
                  breadcrumb.addClass('active');
                  $("#googledrivestarred .breadcrumb .active").removeClass('active');
                  $("#googledrivestarred .breadcrumb").append(breadcrumb);
              }

              self.starredDrive.refreshListForFolder(folder);
          });
       }
    });
};


if (!window.sakai_drive) {
  window.sakai_drive = {};
}

SakaiDrive.prototype.reloadPane = function(href, opts, content) {
    if (!opts) {
        opts = {};
    }

    if (!opts.skip_push) {
        window.history.pushState({'href': href}, "", href);
    }

    if (content) {
        /* No need to fetch anything... */
        $('div.sakai-resources').replaceWith(content);
    } else {
        $.ajax(href,
               {
                   type: "GET",
                   contentType: "html",
                   data: { 'inline': 'true' },
                   cache: false,
               }).success(function (content) {
                   $('div.sakai-resources').replaceWith(content);
               }).error(function () {
                   console.log("FAIL");
                   window.location.href = href;
               });
    }
};

SakaiDrive.prototype.ajaxLinkHandler = function() {
  var self = this;

  var clickHandler = function (e) {
    var $link = $(this);

    if (!$link.data('path')) {
      return true;
    }

    e.preventDefault();
    self.reloadPane($link.attr('href'));
    return false;
  };

  var popstateHandler = function (e) {
    self.reloadPane(window.location.href, { skip_push: true });
  }

  /* FIXME: sux */
  if (!window.sakai_drive.clickhandler_registered) {
    window.sakai_drive.clickhandler_registered = true;
    $(document).on('click', 'a', clickHandler);
  }

  if (!window.sakai_drive.popstate_registered) {
    window.sakai_drive.popstate_registered = true;
    $(window).on('popstate', popstateHandler);
  }
};
